"""
天气工具（agent 的第三个工具）：查新加坡某天各时段会不会下雨，
让 agent 能把户外行程避开雨。

数据源：data.gov.sg 的官方天气接口（NEA 提供），免费、不需要密钥、不用注册。
跟 `singapore_attractions.csv` 是同一个数据来源家族，答辩时"数据都来自政府
开放数据"这句话站得住。

用了两个接口，按查询日期自动选：
1. 24 小时预报 `/v1/environment/24-hour-weather-forecast`
   分 3 个时段（各 6 小时），每个时段还分 5 个区域（north/south/east/west/central）。
   **只有这个接口能回答"下午会不会下雨"**，所以查今天/明天优先用它。
2. 4 天预报 `/v1/environment/4-day-weather-forecast`
   只有整天粒度，没有时段。查更远的日期时用，代价是排不出"上午 vs 下午"。

实测接口返回的天气描述是有限的几种说法（Fair / Windy / Partly cloudy /
Showers / Thundery Showers ...），所以判断下不下雨用关键词匹配就够，
不需要再上一个模型。
"""
import json
import urllib.error
import urllib.request
from datetime import date as date_cls
from datetime import datetime, timedelta, timezone

BASE = "https://api.data.gov.sg/v1/environment"

# 新加坡是 UTC+8。这里写死而不用系统本地时区：服务可能部署在别的时区的机器上，
# 但"用户的行程日期"永远是新加坡当地的日期。
SGT = timezone(timedelta(hours=8))

# 判断"这个时段算不算湿"的关键词。data.gov.sg 的描述用词有限，实测见过：
# Fair / Fair (Day) / Fair (Night) / Partly Cloudy / Cloudy / Windy /
# Light Showers / Showers / Heavy Showers / Thundery Showers / Passing Showers
WET_KEYWORDS = ("shower", "rain", "thunder", "storm", "drizzle")


def _http_get_json(path: str, timeout: int = 15) -> dict:
    with urllib.request.urlopen(f"{BASE}/{path}", timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def is_wet(forecast_text: str) -> bool:
    """一段天气描述算不算"会下雨"。大小写不敏感，匹配不到就当不下雨。"""
    return any(k in (forecast_text or "").lower() for k in WET_KEYWORDS)


def _label_period(start_hour: int) -> str:
    """把接口给的时段起始小时，翻译成排行程用得上的标签。
    接口的 3 个时段固定是 06-12 / 12-18 / 18-06，正好对应上午/下午/晚上。"""
    if 5 <= start_hour < 12:
        return "morning"
    if 12 <= start_hour < 18:
        return "afternoon"
    return "evening"


def get_forecast(target_date: str | None = None, region: str = "central") -> dict | None:
    """
    查某一天的天气。

    target_date: "YYYY-MM-DD"，不传就是今天（新加坡时区）。
    region: north / south / east / west / central，24 小时预报是分区的。
        新加坡很小，默认 central 够用；要更准可以按地点经纬度选区。

    返回 dict：
        {
          "date": "2026-08-08",
          "source": "24h" | "4day",
          "summary": "一句话总结，给前端展示用",
          "periods": [{"label": "afternoon", "forecast": "Showers", "is_wet": true}, ...],
          "has_period_detail": True/False,   # 4day 数据没有时段细分
        }
    查询失败（断网、接口挂了、日期超出预报范围）返回 None——
    **调用方必须能接受 None**，天气查不到时 agent 退回只按距离排序，不能整个挂掉。
    """
    today = datetime.now(SGT).date()
    if target_date:
        try:
            want = date_cls.fromisoformat(target_date)
        except ValueError:
            return None
    else:
        want = today

    days_ahead = (want - today).days
    if days_ahead < 0 or days_ahead > 4:
        # 过去的日期查不了，超过 4 天官方也没有预报
        return None

    try:
        if days_ahead <= 1:
            return _from_24h(want, region)
        return _from_4day(want)
    except (urllib.error.URLError, OSError, KeyError, ValueError, TimeoutError):
        # 天气是"锦上添花"的信号，拿不到不该让整个推荐失败
        return None


def _from_24h(want: date_cls, region: str) -> dict | None:
    data = _http_get_json("24-hour-weather-forecast")
    item = data["items"][0]

    periods = []
    for p in item.get("periods", []):
        start = datetime.fromisoformat(p["time"]["start"])
        if start.date() != want:
            continue
        text = p["regions"].get(region) or p["regions"].get("central", "")
        periods.append(
            {"label": _label_period(start.hour), "forecast": text, "is_wet": is_wet(text)}
        )

    if not periods:
        return None

    wet = [p["label"] for p in periods if p["is_wet"]]
    summary = (
        f"Rain expected in the {', '.join(wet)}" if wet else "No rain expected, good for outdoor stops"
    )
    return {
        "date": want.isoformat(),
        "source": "24h",
        "summary": summary,
        "periods": periods,
        "has_period_detail": True,
    }


def _from_4day(want: date_cls) -> dict | None:
    data = _http_get_json("4-day-weather-forecast")
    for f in data["items"][0].get("forecasts", []):
        if f.get("date") != want.isoformat():
            continue
        text = f.get("forecast", "")
        wet = is_wet(text)
        return {
            "date": want.isoformat(),
            "source": "4day",
            # 没有时段细分，就把整天当成一个时段，让下游逻辑不用写两套
            "summary": f"{text}" + (" (rain likely, prefer indoor stops)" if wet else ""),
            "periods": [{"label": "all_day", "forecast": text, "is_wet": wet}],
            "has_period_detail": False,
        }
    return None


def wet_periods(forecast: dict | None) -> set[str]:
    """从 get_forecast 的结果里挑出会下雨的时段标签，方便排序逻辑直接用。
    forecast 为 None（查不到天气）时返回空集合 = 当作都不下雨，不做特殊处理。"""
    if not forecast:
        return set()
    return {p["label"] for p in forecast["periods"] if p["is_wet"]}


if __name__ == "__main__":
    # 手动冒烟测试：需要能连外网，不需要 Ollama/AWS
    print("--- today ---")
    print(json.dumps(get_forecast(), ensure_ascii=False, indent=2))

    future = (datetime.now(SGT).date() + timedelta(days=3)).isoformat()
    print(f"\n--- {future} (3 days ahead) ---")
    print(json.dumps(get_forecast(future), ensure_ascii=False, indent=2))

    print("\n--- out of range (10 days ahead) ---")
    print(get_forecast((datetime.now(SGT).date() + timedelta(days=10)).isoformat()))

    print("\n--- is_wet() spot checks ---")
    for t in ["Fair (Day)", "Windy", "Showers", "Thundery Showers", "Partly Cloudy"]:
        print(f"  {t:20s} -> wet={is_wet(t)}")
