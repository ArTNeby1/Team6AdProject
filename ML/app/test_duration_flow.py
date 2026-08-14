"""
自测脚本：证明"游玩天数"这条链路两条分支都走得通（2026-08-14 新增）。

要证的东西就一句话 —— **AI 侧不猜天数**：
  抽到了 -> duration_days 给数字，needs_duration_input=false，可以直接排行程
  没抽到 -> duration_days 给 null，needs_duration_input=true，必须先问用户
  没拿到确定天数就调 /plan-itinerary -> 400 NUM_DAYS_REQUIRED，不静默按 1 天排

跟 test_robustness.py 一样不依赖 pytest，直接调函数。跑法（在项目根目录 Team6AdProject 下）：
    python ML/app/test_duration_flow.py

不需要 AWS / Ollama：文件顶部把三个 provider 都锁成 mock 了（必须在 import main
之前设，那几个模块是在 import 时读环境变量决定用哪个 provider 的）。
"""
import json
import os
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

# 必须在 import main / orchestrator 之前 —— 见模块 docstring
os.environ.setdefault("EXTRACT_PROVIDER", "mock")
os.environ.setdefault("FILTER_PROVIDER", "mock")
os.environ.setdefault("RECOMMEND_PROVIDER", "mock")

import main  # noqa: E402
import mock_client  # noqa: E402
from extraction import parse_and_validate  # noqa: E402
from fastapi import HTTPException  # noqa: E402
from orchestrator import PipelineResult  # noqa: E402


def check(name, condition, detail=""):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}" + (f" -> {detail}" if detail else ""))


def _extraction_response_for(text: str) -> dict:
    """走一遍真实的响应组装逻辑（main._extraction_response），不是自己拼个 dict 来测。"""
    extraction = main.parse_and_validate(mock_client.mock_extract(text))
    return main._extraction_response(PipelineResult(cleaned_text=text, extraction=extraction))


def test_duration_stated_no_prompt_needed():
    # 分支 A：用户文本里写了天数 -> 直接拿去排行程，不该弹窗
    body = _extraction_response_for("a 3-day trip in Singapore, Gardens by the Bay")
    check(
        "text states duration -> duration_days set, needs_duration_input=false",
        body["duration_days"] == 3 and body["needs_duration_input"] is False,
        f"duration_days={body['duration_days']!r}, needs_duration_input={body['needs_duration_input']!r}",
    )


def test_duration_missing_asks_user():
    # 分支 B：用户没说天数 -> 明确告诉前端"去弹窗问"，而不是丢一个 null 让它自己猜
    body = _extraction_response_for("I want to see Gardens by the Bay and eat chicken rice")
    check(
        "text has no duration -> duration_days=None, needs_duration_input=true",
        body["duration_days"] is None and body["needs_duration_input"] is True,
        f"duration_days={body['duration_days']!r}, needs_duration_input={body['needs_duration_input']!r}",
    )


def test_needs_duration_input_always_present():
    # 这个字段必须**恒定存在**，前端才能无脑读。曾经的坑：只在缺天数时才加字段，
    # 前端 `if (res.needs_duration_input)` 在另一条分支上读到 undefined，行为要看
    # 语言的真值规则，很容易出错。
    for text in ("a 3-day trip", "no duration here"):
        body = _extraction_response_for(text)
        check(
            f"needs_duration_input present for {text!r}",
            "needs_duration_input" in body and isinstance(body["needs_duration_input"], bool),
        )


_PLACES = [
    {"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": []},
    {"name": "Jewel Changi Airport", "type": "attraction", "lat": 1.3601, "lng": 103.9895, "activities": []},
    {"name": "Sentosa", "type": "attraction", "lat": 1.2494, "lng": 103.8303, "activities": []},
]


def test_plan_itinerary_rejects_missing_num_days():
    # 最关键的一条：漏传天数必须**吵**。以前 default=1，漏传会静默返回 200 + 1 天行程，
    # 用户说了玩3天却拿到1天，全链路没有任何地方会报错。
    try:
        main.plan_itinerary(main.PlanItineraryRequest(places=_PLACES))
    except HTTPException as e:
        check(
            "missing num_days -> 400 NUM_DAYS_REQUIRED",
            e.status_code == 400 and str(e.detail).startswith("NUM_DAYS_REQUIRED"),
            f"status={e.status_code}, detail={str(e.detail)[:60]}...",
        )
    else:
        check("missing num_days -> 400 NUM_DAYS_REQUIRED", False, "no exception raised (silently planned 1 day?)")


def test_plan_itinerary_honours_num_days():
    # 分支 A 的下半段：给了天数就真的按那个天数排，days 长度恒等于 num_days
    for num_days in (1, 3, 5):
        result = main.plan_itinerary(main.PlanItineraryRequest(places=_PLACES, num_days=num_days))
        days = result["days"]
        check(
            f"num_days={num_days} -> len(days)=={num_days}",
            len(days) == num_days,
            f"got {len(days)}",
        )


def test_plan_itinerary_rejects_out_of_range_num_days():
    # 越界在 Pydantic 那一层就被拦掉（ge=1, le=30），不会传到 planner 里
    from pydantic import ValidationError

    for bad in (0, 31):
        try:
            main.PlanItineraryRequest(places=_PLACES, num_days=bad)
        except ValidationError:
            check(f"num_days={bad} rejected by schema", True)
        else:
            check(f"num_days={bad} rejected by schema", False, "accepted an out-of-range value")


def test_mock_duration_parser():
    # mock 的正则本身（只影响 mock 模式，真实链路是模型抽的）。
    # 测试输入一律用英文：这些字符串会打印到 Windows 控制台，中文在默认代码页下
    # 显示成乱码（跑出来是 '����ȥ�¼�����5��'），看不出测的到底是哪条用例。
    cases = [
        ("a 3-day trip in Singapore", 3),
        ("I'll stay 5 days", 5),
        ("a 5 day holiday in Singapore", 5),
        ("Day 1: Gardens. Day 2: Sentosa. Day 3: Jewel.", 3),
        ("I want to see Gardens by the Bay", None),
        ("a 999-day trip", None),
    ]
    for text, expected in cases:
        got = mock_client.mock_duration_days(text)
        check(f"mock_duration_days({text!r})", got == expected, f"got {got!r}, expected {expected!r}")


def _validated_duration(raw_duration) -> object:
    """走真实的 parse_and_validate（含天数降级那一步），返回校验后的 duration_days。"""
    payload = json.dumps(
        {
            "destination": "Singapore",
            "dates": [],
            "duration_days": raw_duration,
            "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}],
        }
    )
    return parse_and_validate(payload).duration_days


def test_unusable_duration_degrades_instead_of_502():
    # 2026-08-14 新增。实测过的真实场景：输入 "I am doing a 200-day trip" 时，
    # Bedrock 忠实地抽出 200（模型没错，文本就这么写的），但 200 超出 schema 的
    # 1~30 -> 校验失败 -> 重试 3 次（3 次真实模型调用，每次都照样答 200）->
    # 整条请求 502，**连 destination/places 一起丢掉**。
    # 期望行为：天数没法用就当"没抽到"，其余字段照常返回，前端弹窗问用户。
    cases = [
        (200, None, "way over the 30-day cap"),
        (31, None, "one over the cap"),
        (0, None, "under the 1-day floor"),
        (-3, None, "negative"),
        (3.5, None, "not a whole number of days"),
        (True, None, "bool must not sneak through as 1 day"),
        ("a week", None, "not a number at all"),
        (3, 3, "in range, untouched"),
        ("3", 3, "numeric string still usable"),
        (3.0, 3, "integral float still usable"),
        (30, 30, "exactly at the cap, still usable"),
        (1, 1, "exactly at the floor, still usable"),
        (None, None, "already absent"),
    ]
    for raw, expected, why in cases:
        try:
            got = _validated_duration(raw)
        except Exception as e:  # noqa: BLE001 - 抽取整体不该因为天数而失败
            check(f"duration_days={raw!r} -> {expected!r} ({why})", False, f"raised {type(e).__name__}: {e}")
            continue
        check(f"duration_days={raw!r} -> {expected!r} ({why})", got == expected, f"got {got!r}")


def test_unusable_duration_keeps_the_rest_of_the_extraction():
    # 上面那条的另一半：降级之后 places/destination 必须**原样还在**，
    # 而且 needs_duration_input 要变成 true（= 前端该弹窗了）。
    payload = json.dumps(
        {
            "destination": "Singapore",
            "dates": [],
            "duration_days": 200,
            "places": [
                {"name": "Gardens by the Bay", "type": "attraction", "activities": ["Cloud Forest"]},
                {"name": "Sentosa", "type": "attraction", "activities": []},
            ],
        }
    )
    extraction = parse_and_validate(payload)
    body = main._extraction_response(PipelineResult(cleaned_text="", extraction=extraction))
    check(
        "out-of-range duration -> places survive, needs_duration_input=true",
        len(body["places"]) == 2
        and body["destination"] == "Singapore"
        and body["duration_days"] is None
        and body["needs_duration_input"] is True,
        f"places={len(body['places'])}, duration_days={body['duration_days']!r}, "
        f"needs_duration_input={body['needs_duration_input']!r}",
    )


def test_other_schema_violations_still_retry():
    # 反向保证：天数那条降级**只**管 duration_days，不能变成"吞掉所有校验错误"的
    # 万能补丁。type 写了枚举外的值，照样要抛 ValidationError 让上层重试。
    from pydantic import ValidationError

    payload = json.dumps(
        {
            "destination": "Singapore",
            "duration_days": 200,  # 同时越界，确认它不会顺手把下面这条错误也吞掉
            "places": [{"name": "National Museum", "type": "museum", "activities": []}],
        }
    )
    try:
        parse_and_validate(payload)
    except ValidationError:
        check("invalid place type still raises (retry path intact)", True)
    else:
        check("invalid place type still raises (retry path intact)", False, "validation was swallowed")


if __name__ == "__main__":
    test_duration_stated_no_prompt_needed()
    test_duration_missing_asks_user()
    test_needs_duration_input_always_present()
    test_plan_itinerary_rejects_missing_num_days()
    test_plan_itinerary_honours_num_days()
    test_plan_itinerary_rejects_out_of_range_num_days()
    test_mock_duration_parser()
    test_unusable_duration_degrades_instead_of_502()
    test_unusable_duration_keeps_the_rest_of_the_extraction()
    test_other_schema_violations_still_retry()
