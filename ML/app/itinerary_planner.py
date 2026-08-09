"""
把用户确认的地点排出一个合理顺序（`ordered_stops`）—— agent 的第四件事。

这一步回答的是"先去哪、后去哪"，跟 recommend_agent 的"还能去哪"是两件事：
  recommend_agent   -> suggested_additions：推荐用户没提过的新地点
  这个文件            -> ordered_stops：把用户**自己确认的**地点重排 + 给理由

排序依据两条，按优先级：
1. **天气**（主要）：下雨的时段排室内地点，不下雨的时段排室外地点。
   这是整个 agent 最直观的卖点——"下午有雨，把户外的花园挪到上午"。
2. **距离**（次要）：同一时段内的地点，按彼此距离串成一条不来回折返的线路。

天气查不到（断网 / 日期超出预报范围 / 用户没给日期）时不报错，
自动退化成"只按距离排"，理由里也不会提天气。这一点很重要：
天气是加分项，不该成为单点故障。
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from geo import haversine_km  # noqa: E402
from weather import get_forecast, wet_periods  # noqa: E402

# 一天分成哪几段、按什么顺序走。跟 weather.py 的 _label_period 返回值对齐。
DAY_PARTS = ("morning", "afternoon", "evening")


def _lookup_indoor_outdoor(name: str, dataset_lookup: dict | None) -> str:
    """
    查一个地点是室内还是室外。

    用户确认的地点不一定在我们的 107 条数据集里（可能是他自己打的名字），
    查不到就返回 "unknown"，排序时当成"哪个时段都行"，不硬塞。
    """
    if not dataset_lookup:
        return "unknown"
    return dataset_lookup.get(name.strip().lower(), "unknown")


def _order_by_proximity(places: list[dict]) -> list[dict]:
    """
    同一时段内的地点串成一条尽量短的线路：从第一个开始，每次走去最近的下一个
    （最近邻法）。不是最优解（那是旅行商问题），但对一天两三个地点已经足够，
    而且结果稳定、能解释——答辩被问"怎么排的"能一句话说清。

    缺坐标的地点排在最后：没坐标就没法算距离，硬排会打乱有坐标的那条线。
    """
    with_coords = [p for p in places if p.get("lat") is not None and p.get("lng") is not None]
    without = [p for p in places if p.get("lat") is None or p.get("lng") is None]

    if len(with_coords) <= 1:
        return with_coords + without

    remaining = with_coords[1:]
    route = [with_coords[0]]
    while remaining:
        last = route[-1]
        nearest = min(
            remaining,
            key=lambda p: haversine_km(last["lat"], last["lng"], p["lat"], p["lng"]),
        )
        route.append(nearest)
        remaining.remove(nearest)
    return route + without


def plan_ordered_stops(
    places: list[dict],
    target_date: str | None = None,
    dataset_lookup: dict | None = None,
) -> tuple[list[dict], dict | None]:
    """
    places: 用户确认后的地点，形状跟 /recommend 请求里的一致
        （name / type / lat / lng / activities）。
    target_date: "YYYY-MM-DD"，不传就不查天气，只按距离排。
    dataset_lookup: {地点名小写: "indoor"/"outdoor"}，从数据集来。
        传 None 就所有地点都算 unknown，天气排序失效但不报错。

    返回 (ordered_stops, forecast)：
        ordered_stops 每条 = 原地点字段 + order / time_of_day / is_outdoor / reason
        forecast 是 weather.get_forecast() 的原始结果，调用方拿去填 weather_summary。
    """
    if not places:
        return [], None

    forecast = get_forecast(target_date) if target_date else None
    wet = wet_periods(forecast)

    # 有没有时段级别的天气信息。4 天预报只有整天粒度（label 是 all_day），
    # 那种情况下"把户外挪到上午"是做不到的，只能整体提示一下。
    has_periods = bool(forecast and forecast.get("has_period_detail"))

    # 给每个地点标上室内/室外
    for p in places:
        p["_io"] = _lookup_indoor_outdoor(p.get("name", ""), dataset_lookup)

    if not has_periods or not wet:
        # 没有可用的时段天气信息 -> 纯按距离排一条线
        ordered = _order_by_proximity(places)
        stops = []
        for i, p in enumerate(ordered, start=1):
            stops.append(_build_stop(p, i, None, _distance_reason(p, ordered, i, forecast)))
        return stops, forecast

    # 有雨：把室外地点塞进不下雨的时段，室内地点塞进下雨的时段
    dry_parts = [d for d in DAY_PARTS if d not in wet]
    wet_parts = [d for d in DAY_PARTS if d in wet]

    outdoor = [p for p in places if p["_io"] == "outdoor"]
    indoor = [p for p in places if p["_io"] == "indoor"]
    unknown = [p for p in places if p["_io"] not in ("indoor", "outdoor")]

    # unknown 的跟着室内走：下雨时室内更保险，猜错的代价比让人淋雨小
    indoor += unknown

    buckets: dict[str, list[dict]] = {d: [] for d in DAY_PARTS}
    _spread(outdoor, dry_parts or list(DAY_PARTS), buckets)
    _spread(indoor, wet_parts or list(DAY_PARTS), buckets)

    stops, order = [], 1
    for part in DAY_PARTS:
        if not buckets[part]:
            continue
        for p in _order_by_proximity(buckets[part]):
            stops.append(_build_stop(p, order, part, _weather_reason(p, part, wet, forecast)))
            order += 1
    return stops, forecast


def _spread(items: list[dict], parts: list[str], buckets: dict[str, list[dict]]) -> None:
    """把一批地点尽量平均地分到给定的几个时段里，避免全挤在同一段。"""
    if not items or not parts:
        return
    for i, p in enumerate(items):
        buckets[parts[i % len(parts)]].append(p)


def _build_stop(place: dict, order: int, part: str | None, reason: str) -> dict:
    return {
        "name": place.get("name"),
        "type": place.get("type"),
        "lat": place.get("lat"),
        "lng": place.get("lng"),
        "activities": place.get("activities", []),
        "order": order,
        "time_of_day": part,
        "is_outdoor": place["_io"] == "outdoor" if place.get("_io") != "unknown" else None,
        "reason": reason,
    }


def _weather_reason(place: dict, part: str, wet: set[str], forecast: dict | None) -> str:
    io = place.get("_io")
    raining = part in wet
    if io == "outdoor" and not raining:
        return f"Outdoor stop, scheduled for the {part} to avoid the rain."
    if io == "indoor" and raining:
        return f"Indoor stop, so the {part} rain does not matter."
    if io == "outdoor" and raining:
        return f"Outdoor stop but every slot has rain today; bring an umbrella."
    return f"Scheduled for the {part}."


def _distance_reason(place: dict, ordered: list[dict], index: int, forecast: dict | None) -> str:
    """没有天气信息时的理由：说清楚是按距离排的，别让前端展示一句空话。"""
    if index == 1:
        return "Starting point of the route."
    prev = ordered[index - 2]
    if all(x.get("lat") is not None for x in (prev, place)):
        d = haversine_km(prev["lat"], prev["lng"], place["lat"], place["lng"])
        return f"About {d:.1f}km from the previous stop, kept next to shorten the route."
    return "Ordered after the previous stop."


if __name__ == "__main__":
    # 手动冒烟测试：需要能连外网查天气，不需要 Ollama
    import json
    from datetime import datetime, timedelta

    import pandas as pd

    csv = Path(__file__).resolve().parent.parent / "data" / "processed" / "singapore_attractions.csv"
    df = pd.read_csv(csv)
    lookup = {str(r["name"]).strip().lower(): r["indoor_outdoor"] for _, r in df.iterrows()}

    demo = [
        {"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["photos"]},
        {"name": "National Museum of Singapore", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["exhibition"]},
        {"name": "Marina Barrage", "type": "attraction", "lat": 1.2807, "lng": 103.8713, "activities": ["kite flying"]},
    ]
    today = datetime.now().date().isoformat()
    stops, fc = plan_ordered_stops([dict(p) for p in demo], target_date=today, dataset_lookup=lookup)
    print("weather:", fc["summary"] if fc else None)
    for s in stops:
        print(f"  {s['order']}. [{s['time_of_day']}] {s['name'][:38]:40s} outdoor={s['is_outdoor']}")
        print(f"     {s['reason']}")

    print("\n--- no date given (distance only) ---")
    stops2, fc2 = plan_ordered_stops([dict(p) for p in demo], target_date=None, dataset_lookup=lookup)
    for s in stops2:
        print(f"  {s['order']}. {s['name'][:38]:40s} {s['reason']}")
