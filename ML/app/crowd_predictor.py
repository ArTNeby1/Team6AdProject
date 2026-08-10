"""
F-33"人流预测"的原型：给一个日期，返回"这个季度历史上算旺季还是淡季"。

**先说清楚这不是什么**（答辩必问，如实说）：
backlog 原话是"预测景点人流量情况，以考虑出行顺序"——听起来像是"滨海湾花园
周六下午人多不多"这种景点级别、天级别的问题。这个模块答不了那个问题。
能拿到的公开数据（`TourismReceiptsByMajorComponentsYearToDateQuarterly.csv`，
data.gov.sg）是**全国季度**旅游收入统计，没有任何单一景点的人流数字，
颗粒度也只到"季度"，不到"哪一天""哪个时段"。

所以这里做的是**季节性代理信号**：用历史上"旅游、娱乐、博彩"这一类收入占
全年比例（`scripts/prepare_crowd_data.py` 算出来的 `crowd_seasonal_index.csv`），
回答"这个季度整体来说是旺季还是淡季"，回答不了景点级别、天级别的问题。
跟 `itinerary_planner.py` 的天气排序类比一下：天气是"今天下不下雨"这种能落到
具体一天的信号，这个模块是"这三个月整体是不是旺季"这种更粗的季节信号，
两者精细程度差一个数量级，不能划等号。

**还没接进生产链路**（main.py / orchestrator.py 都没调它）——原型阶段先独立
验证这条路能不能用，跟当初 `content_recommender.py` 刚写完时的状态一样。
要不要接、怎么接（比如在 `/plan-itinerary` 的返回里加一个 `season_note` 字段）
留给团队一起决定。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/crowd_predictor.py
"""
import functools
from datetime import date as date_cls
from pathlib import Path

import pandas as pd

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "processed" / "crowd_seasonal_index.csv"

# 指数是"占全年的比例"，四个季度加起来=1.0，均匀分布时每季度应该是 0.25。
# 拿实际指数跟这个基准比，超出/低于多少算"旺"/"淡"是这里自己定的门槛，
# 不是数据本身给的标准答案——换一个更保守或更宽松的门槛，档位会跟着变。
UNIFORM_BASELINE = 0.25
HIGH_THRESHOLD = 1.05  # 指数 > 基准的 1.05 倍算"旺季"
LOW_THRESHOLD = 0.95  # 指数 < 基准的 0.95 倍算"淡季"


@functools.lru_cache(maxsize=1)
def _load_seasonal_index() -> dict[int, dict]:
    """惰性加载 + 缓存，跟 content_recommender._load_dataset() 同一个模式：
    模块被 import 时不强制要求已经跑过 prepare_crowd_data.py，真正调用时才读文件。"""
    df = pd.read_csv(DATA_PATH)
    return {int(r["quarter"]): {"seasonal_index": float(r["seasonal_index"]),
                                 "n_years_used": int(r["n_years_used"])}
            for _, r in df.iterrows()}


def _quarter_of(d: date_cls) -> int:
    return (d.month - 1) // 3 + 1


def _classify(index: float) -> str:
    if index > UNIFORM_BASELINE * HIGH_THRESHOLD:
        return "high"
    if index < UNIFORM_BASELINE * LOW_THRESHOLD:
        return "low"
    return "medium"


def get_seasonal_crowd_hint(target_date: str) -> dict:
    """
    target_date: "YYYY-MM-DD"。

    返回：
        {
          "quarter": 3,
          "seasonal_index": 0.2556,       # 该季度历史上占全年游客/收入的比例
          "level": "medium",              # low / medium / high，相对均匀分布(0.25)的档位
          "note": "..."                   # 人话解释 + 数据来源，前端可以直接展示
        }

    target_date 格式不对时抛 ValueError——这是调用方传错了参数，
    跟 itinerary_planner 里"外部服务查不到就返回 None"是两类问题：
    这里没有"外部服务"，格式错纯粹是调用方的 bug，不该假装能算出结果。
    """
    try:
        d = date_cls.fromisoformat(target_date)
    except ValueError:
        raise ValueError(f"target_date must be in YYYY-MM-DD format, got {target_date!r}")

    quarter = _quarter_of(d)
    index_table = _load_seasonal_index()
    entry = index_table[quarter]
    level = _classify(entry["seasonal_index"])

    note = (
        f"Q{quarter} has historically accounted for about {entry['seasonal_index'] * 100:.0f}% "
        f"of Singapore's annual tourism receipts (avg of {entry['n_years_used']} years, "
        "pandemic years 2020-2022 excluded) — a "
        f"{'busier-than-average' if level == 'high' else 'quieter-than-average' if level == 'low' else 'roughly average'} "
        "season nationwide. This is a national quarterly proxy, not a per-attraction or per-day forecast."
    )

    return {
        "quarter": quarter,
        "seasonal_index": entry["seasonal_index"],
        "level": level,
        "note": note,
    }


if __name__ == "__main__":
    # 手动冒烟测试：不需要联网/Ollama，纯本地读处理好的 CSV
    for d in ("2026-01-15", "2026-04-15", "2026-07-15", "2026-11-15"):
        hint = get_seasonal_crowd_hint(d)
        print(f"{d} -> Q{hint['quarter']}  index={hint['seasonal_index']}  level={hint['level']}")
        print(f"  {hint['note']}")
