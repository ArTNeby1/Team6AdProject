"""
给 F-32（顺序优化）的 2-opt 完善做定量评估：跟纯最近邻比，线路到底短了多少。

方法：从真实数据集里随机抽几批地点（固定种子，可复现），分别用"纯最近邻"
和"最近邻 + 2-opt"（`itinerary_planner._order_by_proximity` 现在的实现）
排一遍，比较两条线路的总距离。

**如实说明这个指标衡量的是什么**：这里比的是"排出来的线路总距离"，
不是"用户会不会更满意"——2-opt 减少的是直线距离总和，跟真实通勤时间
（要走路/公交/地铁线路）不完全是一回事，这跟 geo.py 里说的直线距离局限
是同一个问题，2-opt 只是在直线距离这个近似模型内做到更优。

跑法（在 ML/ 目录下）：
    python scripts/evaluate_routing.py
"""
import random
import sys
from pathlib import Path
from statistics import mean

APP_DIR = Path(__file__).resolve().parent.parent / "app"
sys.path.insert(0, str(APP_DIR))
sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")  # Windows 终端默认 GBK

from content_recommender import _load_dataset  # noqa: E402
from geo import haversine_km  # noqa: E402
from itinerary_planner import _order_by_proximity, _route_length_km, _two_opt  # noqa: E402

SEED = 7
N_TRIALS = 50
STOPS_PER_DAY = 6  # 典型的一天行程地点数量


def _nearest_neighbor_only(places: list[dict]) -> list[dict]:
    """跟 _order_by_proximity 曾经的实现一样：只做最近邻，不跑 2-opt。
    单独复刻一份，是为了能跟"最近邻 + 2-opt"公平对比——两者除了有没有 2-opt 这一步，
    其余（怎么选起点、怎么处理缺坐标）完全一致。"""
    remaining = places[1:]
    route = [places[0]]
    while remaining:
        last = route[-1]
        nearest = min(remaining, key=lambda p: haversine_km(last["lat"], last["lng"], p["lat"], p["lng"]))
        route.append(nearest)
        remaining.remove(nearest)
    return route


def _build_trials(df, n_trials: int, stops_per_day: int, seed: int) -> list[list[dict]]:
    rng = random.Random(seed)
    rows = df.to_dict("records")
    return [
        [{"name": r["name"], "lat": r["lat"], "lng": r["lng"]} for r in rng.sample(rows, stops_per_day)]
        for _ in range(n_trials)
    ]


if __name__ == "__main__":
    df = _load_dataset()
    trials = _build_trials(df, N_TRIALS, STOPS_PER_DAY, SEED)

    nn_lengths, opt_lengths = [], []
    improved, worse, unchanged = 0, 0, 0

    for trial in trials:
        nn_route = _nearest_neighbor_only([dict(p) for p in trial])
        nn_len = _route_length_km(nn_route)

        opt_route = _order_by_proximity([dict(p) for p in trial])
        opt_len = _route_length_km(opt_route)

        nn_lengths.append(nn_len)
        opt_lengths.append(opt_len)
        if opt_len < nn_len - 1e-9:
            improved += 1
        elif opt_len > nn_len + 1e-9:
            worse += 1
        else:
            unchanged += 1

    total_nn, total_opt = sum(nn_lengths), sum(opt_lengths)
    print(f"{N_TRIALS} 组随机行程，每组 {STOPS_PER_DAY} 个真实地点（种子={SEED}，可复现）\n")
    print(f"纯最近邻总距离：      {total_nn:.1f} km（{N_TRIALS} 组之和）")
    print(f"最近邻 + 2-opt 总距离：{total_opt:.1f} km")
    print(f"整体缩短：             {(1 - total_opt / total_nn) * 100:.1f}%")
    print()
    print(f"逐组结果：{improved} 组变短，{worse} 组变差（应为 0——2-opt 只做能改善的交换），"
          f"{unchanged} 组不变（本来就是局部最优）")
    print()
    print(f"平均单组距离：最近邻 {mean(nn_lengths):.2f} km  ->  2-opt {mean(opt_lengths):.2f} km")

    if worse > 0:
        print("\n⚠️ 出现 2-opt 比纯最近邻更差的情况，说明实现有 bug，需要排查 _two_opt()。")
