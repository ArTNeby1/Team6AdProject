"""
自测脚本：证明 main.py 里"解析+校验+重试"这套防御代码真的顶得住 AI 犯的几种错。
不依赖 pytest，直接调用函数验证，跟项目里其它 validate_*.py 一个风格。

跑法（在项目根目录 Team6AdProject 下）：
    python ML/app/test_robustness.py
"""
import json
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import main  # noqa: E402
import mock_client  # noqa: E402
from fastapi import HTTPException  # noqa: E402
from pydantic import ValidationError  # noqa: E402


def expect_raises(name, exc_type, fn):
    # 这个脚本原本是独立跑的自测脚本，PASS/FAIL 只是打印，不会让 pytest 真的判失败。
    # 现在要接进 CI 的 pytest 里，光打印不够——补一个 assert，pytest 才能真的拦下回归。
    try:
        fn()
    except exc_type as e:
        print(f"[PASS] {name} -> correctly raised {type(e).__name__}")
    else:
        print(f"[FAIL] {name} -> did not raise expected {exc_type.__name__}")
        assert False, f"{name}: did not raise {exc_type.__name__}"


def test_bad_json():
    # 坏情况1：半截 JSON，应该在 json.loads 这一关就报错
    expect_raises(
        "malformed JSON",
        json.JSONDecodeError,
        lambda: main.parse_and_validate(mock_client.mock_extract_bad_json("x")),
    )


def test_missing_field():
    # 坏情况2：JSON 格式没错，但漏了必填字段 name，应该在 schema 校验这一关报错
    expect_raises(
        "JSON missing a required field",
        ValidationError,
        lambda: main.parse_and_validate(mock_client.mock_extract_missing_field("x")),
    )


def test_bad_coords():
    # 坏情况3：坐标是瞎编的、类型也不对(lat给了文字不是数字)，应该在 schema 校验这一关报错
    expect_raises(
        "JSON with invented, wrongly-typed coords",
        ValidationError,
        lambda: main.parse_and_validate(mock_client.mock_extract_bad_coords("x")),
    )


def test_retry_gives_up_after_max_attempts():
    # 模拟一个"永远答错"的模型：重试 MAX_ATTEMPTS 次后应该放弃，抛出 502 错误，而不是无限重试或直接崩溃
    original = main.EXTRACT_FN
    main.EXTRACT_FN = mock_client.mock_extract_bad_json
    try:
        expect_raises(
            "model always wrong: exhausting retries should raise 502",
            HTTPException,
            lambda: main.call_with_retry("any text", "test"),
        )
    finally:
        main.EXTRACT_FN = original  # 测完换回去，不影响后面的测试和真实调用


def test_retry_recovers_when_model_eventually_succeeds():
    # 模拟一个"不稳定"的模型：前几次答错，最后一次终于答对，重试机制应该能兜住并成功返回
    calls = {"count": 0}
    give_up_after = main.MAX_ATTEMPTS

    def flaky(text, source_name="mock"):
        calls["count"] += 1
        if calls["count"] < give_up_after:
            return mock_client.mock_extract_missing_field(text, source_name)
        return mock_client.mock_extract(text, source_name)

    original = main.EXTRACT_FN
    main.EXTRACT_FN = flaky
    try:
        result = main.call_with_retry("any text", "test")
        print(
            f"[PASS] first {give_up_after - 1} attempts wrong, attempt {give_up_after} correct -> "
            f"retry recovered, parsed {len(result.places)} places"
        )
    except Exception as e:
        print(f"[FAIL] retry should have succeeded but raised {type(e).__name__}: {e}")
    finally:
        main.EXTRACT_FN = original


if __name__ == "__main__":
    test_bad_json()
    test_missing_field()
    test_bad_coords()
    test_retry_gives_up_after_max_attempts()
    test_retry_recovers_when_model_eventually_succeeds()
