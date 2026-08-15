"""
自测脚本：证明"用户输入没有可用的旅行信息"（废话/找不到景点）这条链路能被
正确识别成 NO_USEFUL_CONTENT，而不是跟"抽取本身出故障"共用同一个 502。

背景：以前 `places` 是 min_length=1，文本里真的没有地点时模型要么编一个、
要么校验失败触发 3 次白费的重试，最后统一变成一句 502 + 人话错误文本，
后端/前端没法区分"你输入没用"和"AI 服务挂了"。这次改动：
  1. schema 放开 places 允许空列表（trip_models.py + trip_schema.json）
  2. orchestrator.run_extraction() 把"过滤后为空"和"抽到了但 places=[]"
     这两种情况统一标成 error_code="NO_USEFUL_CONTENT"
  3. main.py 把这个 error_code 转成 422（不是 502），detail 带稳定前缀
     "NO_USEFUL_CONTENT:"，后端可以直接按前缀分支转发给前端提示用户重新输入

跟 test_duration_flow.py 一样不依赖 pytest，直接调函数。跑法（在项目根目录
Team6AdProject 下）：
    python ML/app/test_no_useful_content.py

不需要 AWS / Ollama：文件顶部把三个 provider 都锁成 mock 了（必须在 import
main / orchestrator 之前设，那几个模块是在 import 时读环境变量决定用哪个
provider 的，见 test_duration_flow.py 同一处说明）。
"""
import os
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

# 必须在 import main / orchestrator 之前
os.environ.setdefault("EXTRACT_PROVIDER", "mock")
os.environ.setdefault("FILTER_PROVIDER", "mock")
os.environ.setdefault("RECOMMEND_PROVIDER", "mock")

import main  # noqa: E402
import mock_client  # noqa: E402
import orchestrator  # noqa: E402
from extraction import parse_and_validate  # noqa: E402
from fastapi import HTTPException  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from trip_models import TripExtraction  # noqa: E402

client = TestClient(main.app)


def check(name, condition, detail=""):
    # 跟 test_duration_flow.check 一样：光打印不够，CI 靠 assert 才能真的拦下回归。
    print(f"[{'PASS' if condition else 'FAIL'}] {name}" + (f" -> {detail}" if detail else ""))
    assert condition, f"{name}" + (f" -> {detail}" if detail else "")


def test_schema_accepts_empty_places():
    # 核心的放开：以前这行会抛 ValidationError（min_length=1），现在必须通过。
    trip = TripExtraction.model_validate({"destination": "", "places": []})
    check(
        "TripExtraction accepts destination='' + places=[]",
        trip.destination == "" and trip.places == [],
    )


def test_mock_extract_no_content_parses_clean():
    # mock_extract_no_content 模拟"模型老实回没有地点"，不该触发重试/校验错误。
    trip = parse_and_validate(mock_client.mock_extract_no_content("blah blah blah"))
    check(
        "mock_extract_no_content -> parses without retry, places empty",
        trip.places == [] and trip.destination == "",
    )


def test_raw_content_empty_is_no_useful_content():
    # /extract-travel-info 入口：raw_content 是空白字符串（不含真实文字）。
    result = orchestrator.run_extraction(raw_content="   ")
    check(
        "empty raw_content -> error_code=NO_USEFUL_CONTENT",
        result.error_code == "NO_USEFUL_CONTENT",
        f"error_code={result.error_code!r}, error={result.error!r}",
    )


def test_chat_messages_filtered_to_empty_is_no_useful_content():
    # /refine 入口：FILTER_PROVIDER=mock 时 chat_filter 只拼 role=user 的内容，
    # 全是寒暄/assistant 消息时会被滤成空字符串（见 chat_filter.py mock 分支）。
    messages = [
        {"role": "assistant", "content": "Hi! How can I help you plan your trip?"},
        {"role": "user", "content": ""},
    ]
    result = orchestrator.run_extraction(messages=messages)
    check(
        "chat filtered to empty -> error_code=NO_USEFUL_CONTENT",
        result.error_code == "NO_USEFUL_CONTENT",
        f"error_code={result.error_code!r}, error={result.error!r}",
    )


def test_extraction_with_empty_places_is_no_useful_content():
    # raw_content 本身非空（不会被上面那条空文本分支拦住），但抽取忠实地回了
    # places=[]——这是最容易被漏掉的一条分支：文本"有内容"但内容里没有旅行信息。
    original = orchestrator._extract_fn
    orchestrator._extract_fn = mock_client.mock_extract_no_content
    try:
        result = orchestrator.run_extraction(raw_content="今天天气真好啊，心情也不错，哈哈哈")
        check(
            "non-empty text but places=[] -> error_code=NO_USEFUL_CONTENT",
            result.error_code == "NO_USEFUL_CONTENT" and result.extraction is not None
            and result.extraction.places == [],
            f"error_code={result.error_code!r}, extraction={result.extraction!r}",
        )
    finally:
        orchestrator._extract_fn = original


def test_normal_extraction_unaffected():
    # 反向保证：正常抽到地点的情况完全不受这次改动影响。
    result = orchestrator.run_extraction(raw_content="a 3-day trip in Singapore, Gardens by the Bay")
    check(
        "normal extraction -> no error, places non-empty",
        result.error is None and result.error_code is None and len(result.extraction.places) > 0,
        f"error={result.error!r}, places={result.extraction and len(result.extraction.places)}",
    )


def test_genuine_model_failure_still_502_not_no_useful_content():
    # 反向保证：这条降级**只**管"真的没内容"，不能变成"把所有抽取失败都吞成
    # NO_USEFUL_CONTENT"的万能补丁。模型持续吐坏 JSON 时仍然是 EXTRACTION_FAILED。
    original = orchestrator._extract_fn
    orchestrator._extract_fn = mock_client.mock_extract_bad_json
    try:
        result = orchestrator.run_extraction(raw_content="a 3-day trip in Singapore")
        check(
            "model keeps failing -> error_code=EXTRACTION_FAILED (not NO_USEFUL_CONTENT)",
            result.error_code == "EXTRACTION_FAILED",
            f"error_code={result.error_code!r}",
        )
    finally:
        orchestrator._extract_fn = original


def test_extraction_response_maps_no_useful_content_to_422():
    # main._extraction_response 是 /extract-travel-info 和 /refine 共用的组装逻辑：
    # NO_USEFUL_CONTENT 必须是 422 + 稳定前缀，不是 502。
    result = orchestrator.PipelineResult(
        cleaned_text="", error="no extractable content (empty after noise filtering)",
        error_code="NO_USEFUL_CONTENT",
    )
    try:
        main._extraction_response(result)
    except HTTPException as e:
        check(
            "NO_USEFUL_CONTENT -> HTTP 422 with stable prefix",
            e.status_code == 422 and str(e.detail).startswith("NO_USEFUL_CONTENT:"),
            f"status={e.status_code}, detail={e.detail!r}",
        )
    else:
        check("NO_USEFUL_CONTENT -> HTTP 422 with stable prefix", False, "no exception raised")


def test_extraction_response_keeps_502_for_real_failures():
    # 对照组：没有 error_code（老的 ExtractionFailedError 路径）依旧是 502，
    # 证明这次改动没有偷偷把所有错误都改成 422。
    result = orchestrator.PipelineResult(cleaned_text="x", error="model failed after 3 attempts")
    try:
        main._extraction_response(result)
    except HTTPException as e:
        check(
            "no error_code -> still HTTP 502",
            e.status_code == 502,
            f"status={e.status_code}, detail={e.detail!r}",
        )
    else:
        check("no error_code -> still HTTP 502", False, "no exception raised")


def test_http_endpoint_returns_422_for_empty_input():
    # 端到端过一遍真实的 FastAPI 路由（不是直接调内部函数），证明整条链路接得上。
    response = client.post("/extract-travel-info", json={"raw_content": "   "})
    check(
        "POST /extract-travel-info with blank raw_content -> 422 NO_USEFUL_CONTENT",
        response.status_code == 422 and response.json()["detail"].startswith("NO_USEFUL_CONTENT:"),
        f"status={response.status_code}, body={response.json()}",
    )


def test_http_endpoint_still_ok_for_normal_input():
    # 反向保证：正常输入不受影响，走的还是 200。
    response = client.post(
        "/extract-travel-info",
        json={"raw_content": "a 3-day trip in Singapore, Gardens by the Bay"},
    )
    check(
        "POST /extract-travel-info with real content -> 200 OK",
        response.status_code == 200 and response.json()["status"] == "OK",
        f"status={response.status_code}, body={response.json()}",
    )


if __name__ == "__main__":
    test_schema_accepts_empty_places()
    test_mock_extract_no_content_parses_clean()
    test_raw_content_empty_is_no_useful_content()
    test_chat_messages_filtered_to_empty_is_no_useful_content()
    test_extraction_with_empty_places_is_no_useful_content()
    test_normal_extraction_unaffected()
    test_genuine_model_failure_still_502_not_no_useful_content()
    test_extraction_response_maps_no_useful_content_to_422()
    test_extraction_response_keeps_502_for_real_failures()
    test_http_endpoint_returns_422_for_empty_input()
    test_http_endpoint_still_ok_for_normal_input()
