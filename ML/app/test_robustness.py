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
from trip_models import TripExtraction  # noqa: E402


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


def expect_ok(name, fn):
    try:
        fn()
    except Exception as e:
        print(f"[FAIL] {name} -> unexpectedly raised {type(e).__name__}: {e}")
    else:
        print(f"[PASS] {name}")


def test_duration_days_defaults_to_none():
    # duration_days 是选填：文本没说玩几天时，模型不写这个字段也必须能过校验，
    # 拿到的是 None 而不是报错——不能因为加了新字段就把老的抽取结果全判成不合规。
    data = {
        "destination": "Singapore",
        "places": [{"name": "Gardens by the Bay", "type": "attraction"}],
    }
    trip = main.parse_and_validate(json.dumps(data))
    if trip.duration_days is None:
        print("[PASS] duration_days omitted -> defaults to None")
    else:
        print(f"[FAIL] duration_days omitted -> expected None, got {trip.duration_days!r}")


def test_duration_days_accepts_valid_value():
    # mock_extract 的 duration_days 是看输入文本的（见 mock_client.mock_duration_days），
    # 所以这里要给一段真的写了天数的文本，否则测的是 None 那条分支。
    expect_ok(
        "duration_days from 'a 3-day trip' passes validation",
        lambda: main.parse_and_validate(mock_client.mock_extract("a 3-day trip in Singapore")),
    )


def test_duration_days_out_of_range_degrades_to_none():
    # 上限对齐 itinerary_planner.MAX_DAYS=30。越界的天数依然在**抽取这一关**处理掉，
    # 不会传到 /plan-itinerary 才被拒（那时候错误信息指向后端传参，排查方向就错了）。
    #
    # 2026-08-14 改了处理方式：以前是抛 ValidationError（这个测试原来断言的就是它），
    # 现在是降级成 None。原因是实测发现抛错的代价太大 —— 输入 "I am doing a 200-day
    # trip" 时模型忠实地抽出 200，抛错会触发 3 次重试（3 次真实 Bedrock 调用，每次都
    # 照样答 200，因为文本就这么写的），最后整条请求 502，**连 destination/places 一起
    # 丢掉**，用户看到的是"AI 挂了"。降级成 None 之后 needs_duration_input 自动变 true，
    # 前端弹窗问用户玩几天，其余抽取结果照常返回。详见 extraction._null_unusable_duration。
    for bad_value in (0, 31, 200):
        data = {
            "destination": "Singapore",
            "duration_days": bad_value,
            "places": [{"name": "Gardens by the Bay", "type": "attraction"}],
        }
        trip = main.parse_and_validate(json.dumps(data))
        if trip.duration_days is None and len(trip.places) == 1:
            print(f"[PASS] duration_days={bad_value} out of 1..30 -> None, places kept")
        else:
            print(
                f"[FAIL] duration_days={bad_value} out of 1..30 -> expected None + places kept, "
                f"got {trip.duration_days!r} / {len(trip.places)} places"
            )

    # schema 本身的上下限没有被放宽：绕过 parse_and_validate 直接校验仍然会被拒。
    # 这条是防止以后有人把 ge/le 删掉，误以为"反正 extraction.py 会兜底"。
    expect_raises(
        "TripExtraction itself still rejects duration_days=31",
        ValidationError,
        lambda: TripExtraction.model_validate(
            {
                "destination": "Singapore",
                "duration_days": 31,
                "places": [{"name": "Gardens by the Bay", "type": "attraction"}],
            }
        ),
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
    test_duration_days_defaults_to_none()
    test_duration_days_accepts_valid_value()
    test_duration_days_out_of_range_degrades_to_none()
    test_retry_gives_up_after_max_attempts()
    test_retry_recovers_when_model_eventually_succeeds()
