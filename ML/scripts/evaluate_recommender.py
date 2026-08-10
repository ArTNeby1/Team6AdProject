"""
给 content_recommender.py（F-18）做定量评估。

**先说清楚这个脚本能证明什么、不能证明什么**（报告里会重点讲）：
这里做的是**内部一致性检查**——算法有没有按它自己声称的逻辑在工作
（"nearby 模式真的按距离排"、"hybrid 模式真的比纯相似度更偏向近处"）。
这**不是**"用户满不满意""推荐得准不准"这种外部效度检查——那需要真实用户
点击/接受/忽略的数据，这正是 F-29（反馈闭环）要收集的东西，现在还没有，
所以没法做、也不假装能做。这里的三个指标都是"代码是否名副其实"，不是
"推荐质量有多高"，答辩要讲清楚这个区别，不要把两者混着说。

三个指标：
1. **Proximity correlation**（nearby 模式）：结果排名和距离的 Spearman 相关系数，
   应该接近 +1（排名 1 = 最近，排名数字越大代表距离越远，两者同向增长）。
2. **Hybrid vs Similar 的平均距离对比**：证明 hybrid 模式确实比纯相似度模式
   更偏向近处的候选（这是 hybrid 存在的意义）。
3. **Catalog coverage**：一批不同的查询下，top-5 结果覆盖了数据集里多大比例
   的地点——检查推荐是不是每次都吐同样那几个热门地点（多样性差），
   而不是真的对不同查询有区分度。

查询集用固定随机种子从数据集里抽（可复现，不需要真实用户数据）。

跑法（在 ML/ 目录下）：
    python scripts/evaluate_recommender.py
"""
import random
import sys
from pathlib import Path
from statistics import mean

APP_DIR = Path(__file__).resolve().parent.parent / "app"
sys.path.insert(0, str(APP_DIR))
sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")  # Windows 终端默认 GBK

from content_recommender import _load_dataset, recommend_from_dataset  # noqa: E402

SEED = 42
N_QUERIES = 20
QUERY_SIZE = 2  # 每次查询模拟"用户已经计划了 2 个地点"
TOP_N = 5


def _build_query_trips(df, n_queries: int, query_size: int, seed: int) -> list[dict]:
    """
    从真实数据集里随机抽几个地点，拼成"用户已经计划了这些地方"的查询。
    固定种子保证每次跑这个脚本结果都一样，能公平对比不同代码版本。
    """
    rng = random.Random(seed)
    trips = []
    rows = df.to_dict("records")
    for _ in range(n_queries):
        picked = rng.sample(rows, query_size)
        trips.append(
            {
                "destination": "Singapore",
                "places": [
                    {"name": r["name"], "type": r["type"], "lat": r["lat"], "lng": r["lng"], "activities": []}
                    for r in picked
                ],
            }
        )
    return trips


def _spearman(ranks: list[int], values: list[float]) -> float:
    """
    Spearman 秩相关，不依赖 scipy（项目没装它，不想为了这一个指标加依赖）。
    先把 values 转成秩次，再对两组秩次算皮尔逊相关——这是 Spearman 的标准定义。
    """
    def to_rank(xs):
        order = sorted(range(len(xs)), key=lambda i: xs[i])
        r = [0.0] * len(xs)
        for pos, idx in enumerate(order):
            r[idx] = pos + 1
        return r

    n = len(values)
    if n < 2:
        return float("nan")
    rank_x = to_rank(ranks)
    rank_y = to_rank(values)
    mean_x, mean_y = mean(rank_x), mean(rank_y)
    cov = sum((rx - mean_x) * (ry - mean_y) for rx, ry in zip(rank_x, rank_y))
    var_x = sum((rx - mean_x) ** 2 for rx in rank_x)
    var_y = sum((ry - mean_y) ** 2 for ry in rank_y)
    denom = (var_x * var_y) ** 0.5
    return cov / denom if denom else float("nan")


def metric_nearby_proximity_correlation(trips: list[dict]) -> float:
    """nearby 模式下，排名(1,2,3...)和 distance_km 的相关系数应该接近 +1（同向增长）。"""
    correlations = []
    for trip in trips:
        results = recommend_from_dataset(trip, top_n=TOP_N, mode="nearby")
        known = [r for r in results if r["distance_km"] is not None]
        if len(known) < 2:
            continue
        ranks = list(range(1, len(known) + 1))
        distances = [r["distance_km"] for r in known]
        correlations.append(_spearman(ranks, distances))
    return mean(correlations) if correlations else float("nan")


def metric_hybrid_prefers_closer_than_similar(trips: list[dict]) -> dict:
    """hybrid 模式的 top-N 平均距离应该比 similar 模式的更小（更偏向近处）。"""
    hybrid_avgs, similar_avgs, hybrid_wins = [], [], 0
    for trip in trips:
        hybrid = recommend_from_dataset(trip, top_n=TOP_N, mode="hybrid")
        similar = recommend_from_dataset(trip, top_n=TOP_N, mode="similar")

        h_dist = [r["distance_km"] for r in hybrid if r["distance_km"] is not None]
        s_dist = [r["distance_km"] for r in similar if r["distance_km"] is not None]
        if not h_dist or not s_dist:
            continue

        h_avg, s_avg = mean(h_dist), mean(s_dist)
        hybrid_avgs.append(h_avg)
        similar_avgs.append(s_avg)
        if h_avg <= s_avg:
            hybrid_wins += 1

    return {
        "hybrid_avg_distance_km": round(mean(hybrid_avgs), 2) if hybrid_avgs else None,
        "similar_avg_distance_km": round(mean(similar_avgs), 2) if similar_avgs else None,
        "hybrid_wins_pct": round(100 * hybrid_wins / len(hybrid_avgs), 1) if hybrid_avgs else None,
        "n_queries": len(hybrid_avgs),
    }


def metric_catalog_coverage(trips: list[dict], dataset_size: int) -> dict:
    """top-5 结果里出现过的不同地点数 / 数据集总地点数——检查有没有总在推荐同几个热门地点。"""
    seen = set()
    for trip in trips:
        for r in recommend_from_dataset(trip, top_n=TOP_N, mode="hybrid"):
            seen.add(r["name"])
    return {"places_recommended_at_least_once": len(seen), "dataset_size": dataset_size,
            "coverage_pct": round(100 * len(seen) / dataset_size, 1)}


if __name__ == "__main__":
    df = _load_dataset()
    trips = _build_query_trips(df, N_QUERIES, QUERY_SIZE, SEED)

    print(f"查询集：{N_QUERIES} 组，每组 {QUERY_SIZE} 个随机真实地点（种子={SEED}，可复现）\n")

    corr = metric_nearby_proximity_correlation(trips)
    print(f"[1] nearby 模式排名 vs 距离的 Spearman 相关系数：{corr:.3f}"
          f"（理想值接近 +1.0，排名越靠后距离确实越远，说明排序是按距离来的）")

    hybrid_vs_similar = metric_hybrid_prefers_closer_than_similar(trips)
    print(f"\n[2] hybrid vs similar 平均推荐距离：")
    print(f"    hybrid  平均 {hybrid_vs_similar['hybrid_avg_distance_km']} km")
    print(f"    similar 平均 {hybrid_vs_similar['similar_avg_distance_km']} km")
    print(f"    hybrid 比 similar 更近（或相等）的查询占比：{hybrid_vs_similar['hybrid_wins_pct']}%")

    coverage = metric_catalog_coverage(trips, len(df))
    print(f"\n[3] 数据集覆盖率：{coverage['places_recommended_at_least_once']}/{coverage['dataset_size']} "
          f"= {coverage['coverage_pct']}% 的地点在这 {N_QUERIES} 组查询里至少被推荐过一次")

    print("\n" + "=" * 70)
    print("以上都是内部一致性检查（算法是否按声称的逻辑工作），不是推荐质量/用户满意度评估。")
    print("后者需要真实用户反馈数据（F-29），目前还没有。")
