"""
自测脚本：weather.py（data.gov.sg 天气查询 + 下雨判断）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_weather.py
不需要联网：接口调用（_http_get_json）在测试里被替换成假数据，
只测本地逻辑（日期范围校验、时段标签、下雨判断、异常兜底成 None）。
"""
import sys
import urllib.error
from datetime import datetime, timedelta
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import weather  # noqa: E402
from weather import (  # noqa: E402
    SGT,
    _label_period,
    get_forecast,
    is_wet,
    wet_periods,
)


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


class _FrozenClock:
    """把 weather.datetime.now() 锁死在固定时刻，用于测 get_forecast() 内部"今天
    是哪天"的逻辑。

    不这样做的话，"今天"要在测试代码和 get_forecast() 内部分别调用一次
    datetime.now(SGT)，两次调用之间如果刚好跨过新加坡午夜（概率极低但真实存在），
    两边算出的日期会差一天，测试就会莫名其妙地失败——这是一个真实存在但极难复现
    的午夜竞态，用固定时钟直接消除，而不是靠"测试跑得够快"来碰运气。
    fromisoformat 等其它用法原样转发给真的 datetime 类。
    """

    def __init__(self, fixed_now):
        self._fixed_now = fixed_now

    def now(self, tz=None):
        return self._fixed_now

    def __getattr__(self, name):
        return getattr(datetime, name)


def test_is_wet_keyword_matching():
    for text in ("Showers", "Thundery Showers", "light rain", "DRIZZLE"):
        expect(f"is_wet({text!r}) -> True", is_wet(text) is True)
    for text in ("Fair (Day)", "Windy", "Partly Cloudy", "", None):
        expect(f"is_wet({text!r}) -> False", is_wet(text) is False)


def test_label_period_boundaries():
    cases = [(5, "morning"), (11, "morning"), (12, "afternoon"),
             (17, "afternoon"), (18, "evening"), (4, "evening")]
    for hour, expected in cases:
        expect(f"_label_period({hour}) -> {expected}", _label_period(hour) == expected)


def test_wet_periods_none_forecast():
    expect("forecast 为 None 时返回空集合", wet_periods(None) == set())


def test_wet_periods_extracts_wet_labels():
    forecast = {"periods": [
        {"label": "morning", "forecast": "Fair", "is_wet": False},
        {"label": "afternoon", "forecast": "Showers", "is_wet": True},
    ]}
    expect("只挑出 is_wet=True 的时段标签", wet_periods(forecast) == {"afternoon"})


def test_get_forecast_invalid_date_string_returns_none():
    expect("非法日期字符串返回 None", get_forecast("not-a-date") is None)


def test_get_forecast_past_date_returns_none():
    yesterday = (datetime.now(SGT).date() - timedelta(days=1)).isoformat()
    expect("过去的日期返回 None", get_forecast(yesterday) is None)


def test_get_forecast_too_far_ahead_returns_none():
    far = (datetime.now(SGT).date() + timedelta(days=10)).isoformat()
    expect("超过 4 天预报范围返回 None", get_forecast(far) is None)


def test_get_forecast_network_error_returns_none():
    original = weather._http_get_json
    weather._http_get_json = lambda *a, **k: (_ for _ in ()).throw(urllib.error.URLError("boom"))
    try:
        expect("接口调用失败时兜底返回 None（不抛异常）", get_forecast() is None)
    finally:
        weather._http_get_json = original


def test_get_forecast_uses_24h_source_for_today():
    # 用固定时钟锁死"现在"，让假数据里的日期和 get_forecast() 内部算出的"今天"
    # 必然一致——不锁的话两边各调一次 datetime.now(SGT)，理论上可能跨午夜导致
    # 日期对不上而出错（见 _FrozenClock 的说明）。
    fixed_now = datetime.now(SGT)
    today = fixed_now.date()
    fake_24h = {
        "items": [{
            "periods": [
                {"time": {"start": f"{today.isoformat()}T06:00:00+08:00"},
                 "regions": {"central": "Fair"}},
                {"time": {"start": f"{today.isoformat()}T12:00:00+08:00"},
                 "regions": {"central": "Showers"}},
                {"time": {"start": f"{today.isoformat()}T18:00:00+08:00"},
                 "regions": {"central": "Cloudy"}},
            ]
        }]
    }
    original_http = weather._http_get_json
    original_datetime = weather.datetime
    weather._http_get_json = lambda path, timeout=15: fake_24h
    weather.datetime = _FrozenClock(fixed_now)
    try:
        result = get_forecast()
        expect("今天查询走 24h 接口", result is not None and result["source"] == "24h")
        expect("24h 结果带时段细分", result["has_period_detail"] is True)
        expect("下雨时段(afternoon)被正确标出",
               any(p["label"] == "afternoon" and p["is_wet"] for p in result["periods"]))
    finally:
        weather._http_get_json = original_http
        weather.datetime = original_datetime


def test_get_forecast_uses_4day_source_for_later_dates():
    target = (datetime.now(SGT).date() + timedelta(days=3)).isoformat()
    fake_4day = {"items": [{"forecasts": [
        {"date": target, "forecast": "Thundery Showers"},
    ]}]}
    original = weather._http_get_json
    weather._http_get_json = lambda path, timeout=15: fake_4day
    try:
        result = get_forecast(target)
        expect("3 天后的查询走 4day 接口", result is not None and result["source"] == "4day")
        expect("4day 结果没有时段细分", result["has_period_detail"] is False)
        expect("整天被判定为下雨", result["periods"][0]["is_wet"] is True)
    finally:
        weather._http_get_json = original


def test_get_forecast_24h_missing_date_returns_none():
    # 接口返回了数据，但里面没有目标日期对应的时段 -> periods 为空 -> 应返回 None
    fake_24h = {"items": [{"periods": []}]}
    original = weather._http_get_json
    weather._http_get_json = lambda path, timeout=15: fake_24h
    try:
        expect("24h 接口数据里找不到目标日期时返回 None", get_forecast() is None)
    finally:
        weather._http_get_json = original


if __name__ == "__main__":
    test_is_wet_keyword_matching()
    test_label_period_boundaries()
    test_wet_periods_none_forecast()
    test_wet_periods_extracts_wet_labels()
    test_get_forecast_invalid_date_string_returns_none()
    test_get_forecast_past_date_returns_none()
    test_get_forecast_too_far_ahead_returns_none()
    test_get_forecast_network_error_returns_none()
    test_get_forecast_uses_24h_source_for_today()
    test_get_forecast_uses_4day_source_for_later_dates()
    test_get_forecast_24h_missing_date_returns_none()
