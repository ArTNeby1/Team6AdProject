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

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "processed" / "singapore_attractions.csv"


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


def recommend_from_dataset(
    trip_extraction: dict, preference_text: str | None = None, top_n: int = 5
) -> list[dict]:
    """
    trip_extraction: 符合 trip_schema.json 的 dict（destination/places），
        跟 recommend_agent.recommend_places() 的输入形状一致，方便以后对比两种
        方式的推荐结果。
    返回：从真实数据集里选出的 top_n 个候选地点（按余弦相似度降序），排除已经
        在 trip_extraction.places 里出现过的地点（不推荐用户已经去过/已规划的）。
    """
    df = _load_dataset()
    vectorizer, matrix = _fit_vectorizer()

    query_text = _build_query_text(trip_extraction, preference_text)
    query_vec = vectorizer.transform([query_text])
    scores = cosine_similarity(query_vec, matrix)[0]

    already_visited = {p.get("name", "").strip().lower() for p in trip_extraction.get("places", [])}

    ranked_idx = sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)
    results = []
    for idx in ranked_idx:
        row = df.iloc[idx]
        if row["name"].strip().lower() in already_visited:
            continue
        results.append(
            {
                "name": row["name"],
                "type": row["type"],
                "similarity": round(float(scores[idx]), 4),
                "address": row["address"],
                "lat": row["lat"],
                "lng": row["lng"],
            }
        )
        if len(results) >= top_n:
            break
    return results


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
