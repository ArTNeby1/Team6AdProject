"""
自测脚本：证明"计划开始日期"这条链路给出的默认值是**今天**（2026-08-19 新增）。

要证的东西就一句话 —— **只认原文明确写全的日期**（extraction._drop_invented_dates）：
  原文写了完整日期（含年份） -> 原样保留，不替用户改
  原文只写了月/日、只有模糊说法、或者压根没写 -> dates 给空数组，
                                              后端退回默认值"今天"

为什么要有这个测试：dates 的第一条会被后端当成新计划的 startDate
（PlanningService.confirmSession），而这个字段的默认值就该是今天。模型只要从
半个日期里推出个年份来，用户看到的开始日期就是错的 —— 实测 Nova Lite 把
"February 14th" 推成 2026-02-14（过去），也会把 "for a weekend" 落成具体两天。

跟 test_duration_flow.py 一样不依赖 pytest 的 fixture，直接调函数。跑法（在项目根
目录 Team6AdProject 下）：
    python ML/app/test_dates_flow.py

不需要 AWS / Ollama：只测 parse_and_validate 里的确定性纠正逻辑，不调模型。
"""
import json
import sys
from datetime import date, timedelta
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from extraction import parse_and_validate  # noqa: E402


def check(name, condition, detail=""):
    # 跟 test_duration_flow.check 同一种写法：光打印的话 CI 里测试挂了 pytest 也判通过，
    # 补一个 assert 才拦得下回归。
    print(f"[{'PASS' if condition else 'FAIL'}] {name}" + (f" -> {detail}" if detail else ""))
    assert condition, f"{name}" + (f" -> {detail}" if detail else "")


def _dates_for(model_dates: list, source_text: str) -> list:
    """走一遍真实的解析链路（parse_and_validate），不是直接调私有函数来测。"""
    raw = json.dumps({
        "destination": "Singapore",
        "dates": model_dates,
        "duration_days": None,
        "places": [{"name": "Marina Bay Sands", "type": "attraction"}],
    })
    return parse_and_validate(raw, source_text).dates


def test_full_date_written_in_text_is_kept():
    # 用户自己写了完整日期（年份能在原文里找到）-> 保留，这是唯一会覆盖"今天"的情况
    dates = _dates_for(["2026-09-22"], "Singapore trip on 2026-09-22, Marina Bay Sands")
    check("full date written in text -> kept verbatim", dates == ["2026-09-22"], f"{dates}")


def test_full_date_range_kept():
    dates = _dates_for(
        ["2026-09-22", "2026-09-24"], "in Singapore from 2026-09-22 to 2026-09-24"
    )
    check("full date range -> both kept", dates == ["2026-09-22", "2026-09-24"], f"{dates}")


def test_bare_month_day_dropped_english():
    # 实测坑：原文 "starting February 14th" 没写年份，模型只能猜。套今年是过去的日期，
    # 顺延到下一次是 6 个月后的日期，两个都不能当默认值 -> 整条丢掉，退回今天。
    today = date.today()
    for model_date in (f"{today.year}-02-14", f"{today.year + 1}-02-14"):
        dates = _dates_for([model_date], "Singapore trip starting February 14th, Marina Bay Sands")
        check(
            f"bare 'February 14th' (model guessed {model_date}) -> dropped",
            dates == [],
            f"{dates}",
        )


def test_bare_month_day_dropped_chinese():
    today = date.today()
    dates = _dates_for([f"{today.year}-02-14"], "2月14日出发去新加坡，滨海湾金沙")
    check("bare 中文 2月14日 (no year) -> dropped", dates == [], f"{dates}")


def test_bare_numeric_month_day_dropped():
    today = date.today()
    dates = _dates_for([f"{today.year}-02-14"], "flying in on 2/14, staying at Marina Bay Sands")
    check("bare numeric 2/14 (no year) -> dropped", dates == [], f"{dates}")


def test_vague_phrase_date_dropped():
    # 实测坑：Nova Lite 把 "for a weekend" 落成了具体的两天。
    today = date.today()
    fabricated = [(today + timedelta(days=1)).isoformat(), (today + timedelta(days=2)).isoformat()]
    dates = _dates_for(fabricated, "take me to Universal Studios and Jewel Changi for a weekend")
    check("vague 'a weekend' -> all invented dates dropped", dates == [], f"{dates}")


def test_relative_phrase_date_dropped():
    today = date.today()
    dates = _dates_for(
        [(today + timedelta(days=1)).isoformat()], "trip to Singapore next Friday, Chinatown"
    )
    check("vague 'next Friday' -> dropped", dates == [], f"{dates}")


def test_no_date_in_text_stays_empty():
    dates = _dates_for([], "I want to plan a trip to Sentosa and Gardens by the Bay, 3 days")
    check("no date in text -> dates stays empty", dates == [], f"{dates}")


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("\nall date-flow checks passed")
