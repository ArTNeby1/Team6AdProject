"""
S1 "ML模型与数据集准备"任务的原型：内容型（content-based）推荐，用 TF-IDF +
余弦相似度算，不靠 LLM 现场编——这是 F-18（Sprint 2，见 `task allocation.xlsx`
AI/ML 行）要正式接进生产链路的推荐方式，这里先在真实数据集
（`ML/data/processed/singapore_attractions.csv`，107 个新加坡真实景点，来源
见 `prepare_places_dataset.py`）上跑通最小可用版本，证明"数据 + 模型"这条路
可行，同时把数据集准备好留给 F-18 用。

跟 recommend_agent.py 的关系：两者现在是并行的两套推荐实现，没有互相调用。
recommend_agent.py 是已经接进 orchestrator.py 生产链路的 LLM-prompt 版推荐；
这份文件是内容型 ML 版的原型，**还没接进生产链路**——要不要替换/怎么跟 LLM 版
结合是 F-18 的后续工作，需要跟队友商量清楚再定，这里先不动 orchestrator.py。

局限（答辩如果被问，如实说）：
- 数据集只有新加坡 107 个景点，全部是 attraction 类型（数据源本身就是单一
  图层），没有餐厅/酒店，覆盖面窄，且仅限新加坡这一个目的地
- 纯文本相似度，不看地理位置远近、开放时间、人群偏好这些结构化信号，
  这些是 F-18/F-32 要继续补的
"""
import functools
from pathlib import Path

import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from geo import min_distance_to_places

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "processed" / "singapore_attractions.csv"

# 距离衰减的尺度（km）：候选地点离用户行程 DISTANCE_SCALE_KM 时，相似度打对折。
# 选 5km 是因为新加坡整体只有 50km 宽，5km 大致是"同一片区域"的量级；
# 换个国土面积大的城市这个值要重新调。
DISTANCE_SCALE_KM = 5.0


@functools.lru_cache(maxsize=1)
def indoor_outdoor_lookup() -> dict:
    """
    {地点名小写: "indoor"/"outdoor"}，给 itinerary_planner 排顺序时查用。

    这一列是 `ML/scripts/label_indoor_outdoor.py` 生成的。老版本的 CSV 没有这列，
    那种情况返回空 dict——下游会把所有地点当 unknown，天气排序失效但不会报错。
    """
    df = _load_dataset()
    if "indoor_outdoor" not in df.columns:
        return {}
    return {
        str(r["name"]).strip().lower(): r["indoor_outdoor"]
        for _, r in df.iterrows()
        if pd.notna(r.get("indoor_outdoor"))
    }


@functools.lru_cache(maxsize=1)
def _load_dataset() -> pd.DataFrame:
    """惰性加载 + 缓存：这个模块被 import 时不强制要求数据集已经跑过 prepare_places_dataset.py，
    真正调用 recommend_from_dataset() 时才读文件，缺文件会在这里直接报错，方便定位。"""
    df = pd.read_csv(DATA_PATH)
    df["text"] = (df["name"].fillna("") + ". " + df["description"].fillna("")).str.strip()
    return df


@functools.lru_cache(maxsize=1)
def _fit_vectorizer():
    """TF-IDF 在整个候选地点集合上只需要 fit 一次，缓存住避免每次推荐请求都重新算。"""
    df = _load_dataset()
    vectorizer = TfidfVectorizer(stop_words="english")
    matrix = vectorizer.fit_transform(df["text"])
    return vectorizer, matrix


def _build_query_text(trip_extraction: dict, preference_text: str | None) -> str:
    """把已抽取的行程（目的地+已规划地点+活动）和偏好文字拼成一段"查询文本"，
    跟候选地点的 name+description 用同一个 TF-IDF 空间比相似度。"""
    parts = [trip_extraction.get("destination", "")]
    for place in trip_extraction.get("places", []):
        parts.append(place.get("name", ""))
        parts.extend(place.get("activities", []))
    if preference_text:
        parts.append(preference_text)
    return ". ".join(p for p in parts if p)


def _proximity(distance_km: float | None) -> float:
    """
    把"多少公里"换算成 0~1 的就近程度，用来跟相似度相乘。

    公式 1/(1+d/scale)：距离 0 时是 1（完全不打折），距离等于 scale 时打对折，
    再远继续衰减但永远不会变成负数或 0——所以一个特别贴题的远景点仍有机会入选，
    只是要比近处的更贴题才行，这比"超过 X 公里直接砍掉"更温和。

    距离未知（用户地点还没地理编码）时返回 1.0，等于不参与打分，
    退化成纯文本相似度排序，而不是把它当成"距离 0"占便宜。
    """
    if distance_km is None:
        return 1.0
    return 1.0 / (1.0 + distance_km / DISTANCE_SCALE_KM)


def recommend_from_dataset(
    trip_extraction: dict,
    preference_text: str | None = None,
    top_n: int = 5,
    mode: str = "hybrid",
    max_distance_km: float | None = None,
) -> list[dict]:
    """
    trip_extraction: 符合 trip_schema.json 的 dict（destination/places），
        跟 recommend_agent.recommend_places() 的输入形状一致，方便对比两种方式的结果。
        places 里带 lat/lng 时才能算距离——抽取阶段的 coords 是 null，
        要等后端地理编码补上，所以 /recommend 这一步传进来的才有坐标。

    mode: 对应 F-18 标题里的 "nearby or similar"，三选一
        - "similar"：只看文字相似度，忽略距离（原来的行为）
        - "nearby" ：只看距离，越近越靠前（没坐标的排最后）
        - "hybrid" ：相似度 × 就近程度，默认值，两者都要

    max_distance_km: 传了就把超过这个距离的候选直接排除（距离未知的保留）。

    返回：top_n 条候选，每条带 similarity / distance_km / score，
        并排除已经在 trip_extraction.places 里出现过的地点。
    """
    df = _load_dataset()
    vectorizer, matrix = _fit_vectorizer()

    query_text = _build_query_text(trip_extraction, preference_text)
    query_vec = vectorizer.transform([query_text])
    scores = cosine_similarity(query_vec, matrix)[0]

    user_places = trip_extraction.get("places", [])
    already_visited = {p.get("name", "").strip().lower() for p in user_places}

    candidates = []
    for idx in range(len(df)):
        row = df.iloc[idx]
        if row["name"].strip().lower() in already_visited:
            continue

        similarity = float(scores[idx])
        distance_km = min_distance_to_places(float(row["lat"]), float(row["lng"]), user_places)

        if max_distance_km is not None and distance_km is not None and distance_km > max_distance_km:
            continue

        if mode == "similar":
            score = similarity
        elif mode == "nearby":
            # 距离未知的用 inf，保证它们排在所有已知距离的后面而不是最前面
            score = -(distance_km if distance_km is not None else float("inf"))
        else:
            score = similarity * _proximity(distance_km)

        candidates.append(
            {
                "name": row["name"],
                "type": row["type"],
                "similarity": round(similarity, 4),
                "distance_km": round(distance_km, 2) if distance_km is not None else None,
                "score": round(score, 4),
                "address": row["address"],
                "lat": float(row["lat"]),
                "lng": float(row["lng"]),
            }
        )

    candidates.sort(key=lambda c: c["score"], reverse=True)
    return candidates[:top_n]


if __name__ == "__main__":
    # 手动冒烟测试：不需要 Ollama/AWS，纯本地跑，证明这条路径独立于 LLM 也能用
    demo_trip = {
        "destination": "Singapore",
        "places": [
            {"name": "Gardens by the Bay", "type": "attraction", "activities": ["Cloud Forest dome"]},
        ],
    }
    for r in recommend_from_dataset(demo_trip, preference_text="museums, history, culture, temples"):
        print(f"{r['similarity']:.3f}  {r['name']}")
