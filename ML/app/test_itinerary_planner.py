"""
自测脚本：证明 ordered_stops 的排序逻辑真的按天气和距离在工作。

为什么要注入假天气：核心卖点是"下午有雨就把户外行程挪到上午"，但新加坡不是
每天都下雨，靠真实接口测的话，不下雨的日子这条分支根本跑不到，等于没测。
所以这里把 weather.get_forecast 换成写死的假预报，把每种天气情况都覆盖一遍。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_itinerary_planner.py
不需要 Ollama，不需要联网（天气被 mock 掉了）。
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import itinerary_planner  # noqa: E402
from itinerary_planner import plan_ordered_stops  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")


# 三个地点：两个室外、一个室内，坐标是真实的
PLACES = [
    {"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["photos"]},
    {"name": "National Museum of Singapore", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["exhibition"]},
    {"name": "Marina Barrage", "type": "attraction", "lat": 1.2807, "lng": 103.8713, "activities": ["kite flying"]},
]

LOOKUP = {
    "gardens by the bay": "outdoor",
    "national museum of singapore": "indoor",
    "marina barrage": "outdoor",
}


def fake_forecast(wet_labels, has_detail=True):
    """造一份假预报：wet_labels 里的时段算下雨，其余不下雨。"""
    parts = ["morning", "afternoon", "evening"] if has_detail else ["all_day"]
    return {
        "date": "2026-08-08",
        "source": "24h" if has_detail else "4day",
        "summary": "test",
        "periods": [
            {"label": p, "forecast": "Showers" if p in wet_labels else "Fair", "is_wet": p in wet_labels}
            for p in parts
        ],
        "has_period_detail": has_detail,
    }


def patch_weather(forecast):
    """把 itinerary_planner 里用到的 get_forecast 换掉，返回还原用的原函数。"""
    original = itinerary_planner.get_forecast
    itinerary_planner.get_forecast = lambda *a, **k: forecast
    return original


def fresh():
    """每次测试都用一份新的地点数据：plan_ordered_stops 会往 dict 里塞 _io 字段。"""
    return [dict(p) for p in PLACES]


def test_afternoon_rain_moves_outdoor_to_morning():
    original = patch_weather(fake_forecast({"afternoon"}))
    try:
        stops, _ = plan_ordered_stops(fresh(), target_date="2026-08-08", dataset_lookup=LOOKUP)
        slot = {s["name"]: s["time_of_day"] for s in stops}
        outdoor_slots = {slot["Gardens by the Bay"], slot["Marina Barrage"]}
        expect(
            f"下午有雨时户外地点不排在下午（实际 {outdoor_slots}）",
            "afternoon" not in outdoor_slots,
        )
        expect(
            f"室内地点被排进下雨的下午（实际 {slot['National Museum of Singapore']}）",
            slot["National Museum of Singapore"] == "afternoon",
        )
    finally:
        itinerary_planner.get_forecast = original


def test_morning_rain_flips_it():
    # 反过来：上午下雨，户外的就该躲开上午。证明不是写死"户外总是排上午"。
    original = patch_weather(fake_forecast({"morning"}))
    try:
        stops, _ = plan_ordered_stops(fresh(), target_date="2026-08-08", dataset_lookup=LOOKUP)
        slot = {s["name"]: s["time_of_day"] for s in stops}
        outdoor_slots = {slot["Gardens by the Bay"], slot["Marina Barrage"]}
        expect(f"上午有雨时户外地点不排在上午（实际 {outdoor_slots}）", "morning" not in outdoor_slots)
    finally:
        itinerary_planner.get_forecast = original


def test_no_rain_falls_back_to_distance():
    original = patch_weather(fake_forecast(set()))
    try:
        stops, _ = plan_ordered_stops(fresh(), target_date="2026-08-08", dataset_lookup=LOOKUP)
        names = [s["name"] for s in stops]
        # Gardens -> Marina Barrage 只有 0.9km，Gardens -> Museum 有 2.4km，
        # 所以按最近邻，Barrage 应该排在 Museum 前面
        expect(
            f"不下雨时按距离串线路（实际 {[n[:22] for n in names]}）",
            names.index("Marina Barrage") < names.index("National Museum of Singapore"),
        )
        expect("不下雨时理由提到距离", any("km" in s["reason"] for s in stops))
    finally:
        itinerary_planner.get_forecast = original


def test_no_date_means_no_weather_call():
    # 不给日期就不该查天气，直接按距离排，且不能报错
    stops, forecast = plan_ordered_stops(fresh(), target_date=None, dataset_lookup=LOOKUP)
    expect("不给日期时 forecast 为 None", forecast is None)
    expect(f"不给日期时仍然返回全部 {len(PLACES)} 个地点", len(stops) == len(PLACES))
    expect("不给日期时 time_of_day 为 None", all(s["time_of_day"] is None for s in stops))


def test_weather_unavailable_does_not_crash():
    # 天气接口挂了（返回 None）时，必须退回距离排序而不是整个失败
    original = patch_weather(None)
    try:
        stops, forecast = plan_ordered_stops(fresh(), target_date="2026-08-08", dataset_lookup=LOOKUP)
        expect("天气拿不到时不报错，仍返回全部地点", len(stops) == len(PLACES))
        expect("天气拿不到时 forecast 为 None", forecast is None)
    finally:
        itinerary_planner.get_forecast = original


def test_unknown_places_are_kept():
    # 用户自己打的地点不在数据集里，查不到室内外，不能被丢掉
    original = patch_weather(fake_forecast({"afternoon"}))
    try:
        places = fresh() + [{"name": "Some Place I Typed", "type": "other", "lat": 1.30, "lng": 103.85, "activities": []}]
        stops, _ = plan_ordered_stops(places, target_date="2026-08-08", dataset_lookup=LOOKUP)
        names = [s["name"] for s in stops]
        expect("数据集里没有的地点也会被保留", "Some Place I Typed" in names)
        unknown = next(s for s in stops if s["name"] == "Some Place I Typed")
        expect("查不到室内外时 is_outdoor 为 None（不瞎猜）", unknown["is_outdoor"] is None)
    finally:
        itinerary_planner.get_forecast = original


def test_places_without_coords_survive():
    original = patch_weather(None)
    try:
        places = [
            {"name": "Gardens by the Bay", "type": "attraction", "lat": None, "lng": None, "activities": []},
            {"name": "Marina Barrage", "type": "attraction", "lat": 1.2807, "lng": 103.8713, "activities": []},
        ]
        stops, _ = plan_ordered_stops(places, target_date=None, dataset_lookup=LOOKUP)
        expect("没有坐标的地点不会被丢掉（后端还没地理编码时）", len(stops) == 2)
    finally:
        itinerary_planner.get_forecast = original


def test_order_is_sequential():
    original = patch_weather(fake_forecast({"afternoon"}))
    try:
        stops, _ = plan_ordered_stops(fresh(), target_date="2026-08-08", dataset_lookup=LOOKUP)
        orders = [s["order"] for s in stops]
        expect(f"order 从 1 连续递增（实际 {orders}）", orders == list(range(1, len(stops) + 1)))
    finally:
        itinerary_planner.get_forecast = original


if __name__ == "__main__":
    test_afternoon_rain_moves_outdoor_to_morning()
    test_morning_rain_flips_it()
    test_no_rain_falls_back_to_distance()
    test_no_date_means_no_weather_call()
    test_weather_unavailable_does_not_crash()
    test_unknown_places_are_kept()
    test_places_without_coords_survive()
    test_order_is_sequential()
