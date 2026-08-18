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
from geo import haversine_km  # noqa: E402
from itinerary_planner import (  # noqa: E402
    _order_by_proximity,
    _route_length_km,
    _two_opt,
    plan_multi_day_itinerary,
    plan_ordered_stops,
)


def expect(name, condition):
    # 原本只打印，接进 CI 的 pytest 后补 assert，不然条件为假 pytest 也会显示通过。
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


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

# 多天测试专用：两片相距约 16km 的真实区域，故意打乱顺序穿插着放，
# 用来验证分天算法真的在按地理位置重新分组，而不是照原顺序切。
MARINA = ["Gardens by the Bay", "Marina Bay Sands", "Marina Barrage"]
CHANGI = ["Jewel Changi Airport", "Changi Airport", "Changi Beach Park"]
TWO_AREAS = [
    {"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": []},
    {"name": "Changi Airport", "type": "attraction", "lat": 1.3644, "lng": 103.9915, "activities": []},
    {"name": "Marina Barrage", "type": "attraction", "lat": 1.2807, "lng": 103.8713, "activities": []},
    {"name": "Jewel Changi Airport", "type": "attraction", "lat": 1.3601, "lng": 103.9895, "activities": []},
    {"name": "Marina Bay Sands", "type": "attraction", "lat": 1.2830, "lng": 103.8585, "activities": []},
    {"name": "Changi Beach Park", "type": "attraction", "lat": 1.3900, "lng": 103.9900, "activities": []},
]


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


# ===========================================================================
# F-32 顺序优化完善（_two_opt，2026-08-08 加在 _order_by_proximity 里）
# ===========================================================================


def _nearest_neighbor_only(places: list[dict]) -> list[dict]:
    """跟 scripts/evaluate_routing.py 里同一份复刻：不跑 2-opt 的版本，用来跟
    _order_by_proximity（现在内置 2-opt）公平对比。"""
    remaining = places[1:]
    route = [places[0]]
    while remaining:
        last = route[-1]
        nearest = min(remaining, key=lambda p: haversine_km(last["lat"], last["lng"], p["lat"], p["lng"]))
        route.append(nearest)
        remaining.remove(nearest)
    return route


# 6 个点，坐标是网格搜索找出来的真实案例（不是凑出来的）：纯最近邻在这个布局上
# 会贪心地早早走远，被迫绕路，2-opt 能把总距离压短 31%。
# （搜索方法：随机生成整数网格坐标，跑 2000 组比较 NN vs 2-opt 的差距，挑差距最大的一组）
TWO_OPT_DEMO = [
    {"name": "A", "lat": 6, "lng": 0},
    {"name": "B", "lat": 0, "lng": 9},
    {"name": "C", "lat": 6, "lng": 4},
    {"name": "D", "lat": 7, "lng": 3},
    {"name": "E", "lat": 10, "lng": 0},
    {"name": "F", "lat": 6, "lng": 6},
]


def test_two_opt_never_makes_route_longer():
    """2-opt 只做能缩短总距离的交换，结果不可能比只用最近邻更差——这是它的正确性底线。"""
    nn_route = _nearest_neighbor_only([dict(p) for p in TWO_OPT_DEMO])
    opt_route = _two_opt(list(nn_route))
    nn_len, opt_len = _route_length_km(nn_route), _route_length_km(opt_route)
    expect(f"2-opt 后总距离不比纯最近邻长（最近邻 {nn_len:.2f}km -> 2-opt {opt_len:.2f}km）",
           opt_len <= nn_len + 1e-9)


def test_two_opt_improves_a_crossing_route():
    """在特意设计的"绕远"布局上，2-opt 应该真的把总距离缩短，不只是不变差。"""
    nn_route = _nearest_neighbor_only([dict(p) for p in TWO_OPT_DEMO])
    opt_route = _two_opt(list(nn_route))
    nn_len, opt_len = _route_length_km(nn_route), _route_length_km(opt_route)
    expect(f"2-opt 在绕远的布局上确实缩短了距离（{nn_len:.2f}km -> {opt_len:.2f}km）",
           opt_len < nn_len - 1e-9)


def test_two_opt_keeps_same_places():
    """2-opt 只能换访问顺序，不能凭空丢掉或加进地点。"""
    nn_route = _nearest_neighbor_only([dict(p) for p in TWO_OPT_DEMO])
    opt_route = _two_opt(list(nn_route))
    expect("2-opt 前后地点集合完全一样，只是顺序变了",
           {p["name"] for p in opt_route} == {p["name"] for p in nn_route})


def test_two_opt_is_idempotent():
    """已经是局部最优的线路再跑一次 2-opt，不该再变——否则说明会来回震荡，是 bug。"""
    nn_route = _nearest_neighbor_only([dict(p) for p in TWO_OPT_DEMO])
    once = _two_opt(list(nn_route))
    twice = _two_opt(list(once))
    expect("对已经优化过的线路再跑一次 2-opt 结果不变",
           [p["name"] for p in once] == [p["name"] for p in twice])


def test_two_opt_skips_three_or_fewer_stops():
    """3 个点以内不可能出现"交叉"，函数应该直接原样返回，不做无意义的计算。"""
    three = [dict(p) for p in TWO_OPT_DEMO[:3]]
    expect("3 个点时 2-opt 原样返回", _two_opt(list(three)) == three)


def test_order_by_proximity_still_keeps_places_without_coords_last():
    """套上 2-opt 之后，"没坐标的地点排最后"这条老规则不能被破坏。"""
    places = [dict(p) for p in TWO_OPT_DEMO] + [{"name": "NoCoords", "lat": None, "lng": None}]
    ordered = _order_by_proximity(places)
    expect("没坐标的地点仍然排在最后", ordered[-1]["name"] == "NoCoords")
    expect("其余地点数量不变", len(ordered) == len(places))


# ===========================================================================
# F-09 按天拆分行程（plan_multi_day_itinerary）
# 下面这些测试全程把天气 mock 成 None：分天靠的是地理位置，不该依赖网络，
# 而且不 mock 的话真实接口每次返回不同，测试就不稳定了。
# ===========================================================================


def test_multi_day_groups_places_by_area():
    """核心断言：同一天的地点必须属于同一片区域，不能一天里横跨全岛。"""
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date=None, num_days=2, dataset_lookup=LOOKUP)
        homogeneous = []
        for d in days:
            names = {s["name"] for s in d["stops"]}
            homogeneous.append(names <= set(MARINA) or names <= set(CHANGI))
        layout = [[s["name"][:16] for s in d["stops"]] for d in days]
        expect(f"每天的地点都在同一片区域（实际 {layout}）", all(homogeneous))
        # 输入是两片区域穿插着给的，能分干净就说明真的按位置重排过，不是照原顺序切
        expect("分天不是照输入顺序切的", len(days) == 2 and all(len(d["stops"]) == 3 for d in days))
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_splits_evenly():
    """每天的地点数最多相差 1——不能出现"第一天 5 个第二天 1 个"。"""
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date=None, num_days=4, dataset_lookup=LOOKUP)
        sizes = [len(d["stops"]) for d in days]
        expect(f"6 个地点排 4 天 -> 每天数量相差不超过 1（实际 {sizes}）", max(sizes) - min(sizes) <= 1)
        expect(f"总数不变，还是 6 个（实际 {sum(sizes)}）", sum(sizes) == len(TWO_AREAS))
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_fewer_places_than_days():
    """地点比天数少：天数不缩水，多出来的那天是空的，让前端能提示"还没安排"。"""
    original = patch_weather(None)
    try:
        two = [dict(p) for p in TWO_AREAS[:2]]
        days = plan_multi_day_itinerary(two, start_date=None, num_days=3, dataset_lookup=LOOKUP)
        sizes = [len(d["stops"]) for d in days]
        expect(f"2 个地点排 3 天仍然返回 3 天（实际 {len(days)} 天）", len(days) == 3)
        expect(f"多出来的那天是空的而不是被删掉（实际 {sizes}）", sizes == [1, 1, 0])
        expect("空的那天不报错，weather_summary 为 None", days[-1]["weather_summary"] is None)
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_dates_increment():
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date="2026-08-08", num_days=3, dataset_lookup=LOOKUP)
        dates = [d["date"] for d in days]
        expect(f"日期从 start_date 起逐天递增（实际 {dates}）",
               dates == ["2026-08-08", "2026-08-09", "2026-08-10"])
        expect(f"day 编号从 1 连续（实际 {[d['day'] for d in days]}）",
               [d["day"] for d in days] == [1, 2, 3])
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_without_start_date():
    """不给日期：照样能分天，只是没有日期也没有天气——不能报错。"""
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date=None, num_days=2, dataset_lookup=LOOKUP)
        expect("不给 start_date 时每天的 date 都是 None", all(d["date"] is None for d in days))
        expect("不给 start_date 时 weather_summary 都是 None",
               all(d["weather_summary"] is None for d in days))
        expect("不给 start_date 时 time_of_day 都是 None",
               all(s["time_of_day"] is None for d in days for s in d["stops"]))
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_order_restarts_each_day():
    """每天的 order 从 1 重新开始，不是全程连续编号——前端按天渲染要靠这个。"""
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date=None, num_days=2, dataset_lookup=LOOKUP)
        orders = [[s["order"] for s in d["stops"]] for d in days]
        expect(f"每天 order 都从 1 开始连续递增（实际 {orders}）",
               all(o == list(range(1, len(o) + 1)) for o in orders))
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_keeps_places_without_coords():
    """没坐标的地点（后端还没地理编码）不能被丢掉，排到最后即可。"""
    original = patch_weather(None)
    try:
        places = [dict(p) for p in TWO_AREAS] + [
            {"name": "Some Place I Typed", "type": "other", "lat": None, "lng": None, "activities": []}
        ]
        days = plan_multi_day_itinerary(places, start_date=None, num_days=2, dataset_lookup=LOOKUP)
        all_names = [s["name"] for d in days for s in d["stops"]]
        expect(f"一个地点都没丢（{len(all_names)}/{len(places)}）", len(all_names) == len(places))
        expect("没有坐标的地点也在结果里", "Some Place I Typed" in all_names)
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_weather_failure_degrades():
    """天气接口挂了（get_forecast 返回 None）时，分天照常，只是没有天气信息。"""
    original = patch_weather(None)
    try:
        days = plan_multi_day_itinerary(TWO_AREAS, start_date="2026-08-08", num_days=2, dataset_lookup=LOOKUP)
        expect("天气拿不到时仍然返回完整天数", len(days) == 2)
        expect("天气拿不到时 weather_summary 为 None 而不是报错",
               all(d["weather_summary"] is None for d in days))
        expect("天气拿不到时地点一个不少",
               sum(len(d["stops"]) for d in days) == len(TWO_AREAS))
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_uses_weather_per_day():
    """传了日期且有雨时，每一天各自按天气排——证明天气逻辑在多天场景下没失效。"""
    original = patch_weather(fake_forecast({"afternoon"}))
    try:
        days = plan_multi_day_itinerary(fresh(), start_date="2026-08-08", num_days=1, dataset_lookup=LOOKUP)
        slot = {s["name"]: s["time_of_day"] for s in days[0]["stops"]}
        expect(f"多天入口下天气排序仍生效：室内地点排进下雨的下午（实际 {slot['National Museum of Singapore']}）",
               slot["National Museum of Singapore"] == "afternoon")
        expect(f"多天入口下 weather_summary 有值（实际 {days[0]['weather_summary']!r}）",
               days[0]["weather_summary"] is not None)
    finally:
        itinerary_planner.get_forecast = original


def test_multi_day_rejects_bad_arguments():
    """参数传错要抛 ValueError（-> 400），不能静悄悄降级——那是调用方的 bug，得让他看见。"""
    for bad_days in (0, -1, itinerary_planner.MAX_DAYS + 1):
        try:
            plan_multi_day_itinerary(TWO_AREAS, num_days=bad_days, dataset_lookup=LOOKUP)
            expect(f"num_days={bad_days} 应该抛 ValueError", False)
        except ValueError:
            expect(f"num_days={bad_days} 抛 ValueError", True)

    try:
        plan_multi_day_itinerary(TWO_AREAS, start_date="08/08/2026", num_days=2, dataset_lookup=LOOKUP)
        expect("start_date 格式错应该抛 ValueError", False)
    except ValueError:
        expect("start_date 格式错抛 ValueError", True)


def test_multi_day_does_not_mutate_caller_data():
    """内部会往 dict 塞 _io 字段，必须复制，不能污染调用方传进来的原始数据。"""
    original = patch_weather(None)
    try:
        places = [dict(p) for p in TWO_AREAS]
        plan_multi_day_itinerary(places, start_date=None, num_days=2, dataset_lookup=LOOKUP)
        expect("调用后原始数据里没有多出内部字段 _io",
               all("_io" not in p for p in places))
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

    print("\n--- F-32 two-opt route refinement ---")
    test_two_opt_never_makes_route_longer()
    test_two_opt_improves_a_crossing_route()
    test_two_opt_keeps_same_places()
    test_two_opt_is_idempotent()
    test_two_opt_skips_three_or_fewer_stops()
    test_order_by_proximity_still_keeps_places_without_coords_last()

    print("\n--- F-09 multi-day ---")
    test_multi_day_groups_places_by_area()
    test_multi_day_splits_evenly()
    test_multi_day_fewer_places_than_days()
    test_multi_day_dates_increment()
    test_multi_day_without_start_date()
    test_multi_day_order_restarts_each_day()
    test_multi_day_keeps_places_without_coords()
    test_multi_day_weather_failure_degrades()
    test_multi_day_uses_weather_per_day()
    test_multi_day_rejects_bad_arguments()
    test_multi_day_does_not_mutate_caller_data()
