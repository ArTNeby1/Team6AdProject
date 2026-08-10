"""
自测脚本：crowd_predictor.py（F-33 季节性代理原型）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_crowd_predictor.py
不需要联网/Ollama，纯本地读 data/processed/crowd_seasonal_index.csv。
"""
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")  # Windows 终端默认 GBK

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import crowd_predictor  # noqa: E402
from crowd_predictor import _classify, _quarter_of, get_seasonal_crowd_hint  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")


def test_quarter_boundaries():
    from datetime import date
    cases = [
        (date(2026, 1, 1), 1), (date(2026, 3, 31), 1),
        (date(2026, 4, 1), 2), (date(2026, 6, 30), 2),
        (date(2026, 7, 1), 3), (date(2026, 9, 30), 3),
        (date(2026, 10, 1), 4), (date(2026, 12, 31), 4),
    ]
    for d, expected_q in cases:
        expect(f"{d.isoformat()} -> Q{expected_q}", _quarter_of(d) == expected_q)


def test_returns_all_expected_fields():
    hint = get_seasonal_crowd_hint("2026-08-08")
    expect("返回包含 quarter", "quarter" in hint)
    expect("返回包含 seasonal_index", "seasonal_index" in hint)
    expect("返回包含 level", "level" in hint)
    expect("返回包含 note（人话解释）", isinstance(hint.get("note"), str) and len(hint["note"]) > 0)
    expect(f"2026-08-08 落在 Q3（实际 {hint['quarter']}）", hint["quarter"] == 3)


def test_level_is_one_of_three_values():
    for month in (1, 4, 7, 10):
        hint = get_seasonal_crowd_hint(f"2026-{month:02d}-15")
        expect(f"Q{hint['quarter']} 的 level 是合法档位（实际 {hint['level']!r}）",
               hint["level"] in ("low", "medium", "high"))


def test_seasonal_index_matches_prepared_csv():
    # 不重新算一遍季节性指数（那是 prepare_crowd_data.py 该测的），
    # 只验证 crowd_predictor 读到的数字真的来自处理好的 CSV，没有被再加工/写死。
    import pandas as pd
    df = pd.read_csv(crowd_predictor.DATA_PATH)
    expected = {int(r["quarter"]): float(r["seasonal_index"]) for _, r in df.iterrows()}
    for q in (1, 2, 3, 4):
        hint = get_seasonal_crowd_hint(f"2026-{(q - 1) * 3 + 1:02d}-01")
        expect(f"Q{q} 的 seasonal_index 跟 CSV 里的值一致（{hint['seasonal_index']} vs {expected[q]}）",
               hint["seasonal_index"] == expected[q])


def test_classify_thresholds():
    baseline = crowd_predictor.UNIFORM_BASELINE
    expect("明显高于基准判为 high", _classify(baseline * 1.5) == "high")
    expect("明显低于基准判为 low", _classify(baseline * 0.5) == "low")
    expect("正好等于基准判为 medium", _classify(baseline) == "medium")


def test_bad_date_format_raises():
    try:
        get_seasonal_crowd_hint("08/08/2026")
        expect("错误的日期格式应该抛 ValueError", False)
    except ValueError:
        expect("错误的日期格式抛 ValueError", True)


def test_note_flags_it_as_national_proxy_not_per_attraction():
    # 这是这个原型最重要的诚实边界：note 里必须提醒"全国级别，不是景点级别"，
    # 不能让调用方误以为这是真的景点人流预测——防止文档漂移之后代码没跟上。
    hint = get_seasonal_crowd_hint("2026-08-08")
    expect("note 里明确说明是全国代理信号，不是景点级预测",
           "national" in hint["note"].lower() and "per-attraction" in hint["note"].lower())


if __name__ == "__main__":
    test_quarter_boundaries()
    test_returns_all_expected_fields()
    test_level_is_one_of_three_values()
    test_seasonal_index_matches_prepared_csv()
    test_classify_thresholds()
    test_bad_date_format_raises()
    test_note_flags_it_as_national_proxy_not_per_attraction()
