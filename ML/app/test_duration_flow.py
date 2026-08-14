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
    # mock 的正则本身（只影响 mock 模式，真实链路是模型抽的）
    cases = [
        ("a 3-day trip in Singapore", 3),
        ("I'll stay 5 days", 5),
        ("我想去新加坡玩5天", 5),
        ("Day 1: Gardens. Day 2: Sentosa. Day 3: Jewel.", 3),
        ("I want to see Gardens by the Bay", None),
        ("a 999-day trip", None),
    ]
    for text, expected in cases:
        got = mock_client.mock_duration_days(text)
        check(f"mock_duration_days({text!r})", got == expected, f"got {got!r}, expected {expected!r}")


if __name__ == "__main__":
    test_duration_stated_no_prompt_needed()
    test_duration_missing_asks_user()
    test_needs_duration_input_always_present()
    test_plan_itinerary_rejects_missing_num_days()
    test_plan_itinerary_honours_num_days()
    test_plan_itinerary_rejects_out_of_range_num_days()
    test_mock_duration_parser()
