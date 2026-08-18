"""
自测脚本：local_llm_client.py（走本地 Ollama 的底层调用函数）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_local_llm_client.py
不需要本机真的跑着 Ollama：requests.post 在测试里被替换成假函数，
只测 local_llm_client 自己组请求/解析响应/装配 prompt 的逻辑。
"""
import sys
from pathlib import Path

import requests

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import local_llm_client  # noqa: E402
from local_llm_client import call_local_model, local_extract  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


def expect_raises(name, exc_type, fn):
    try:
        fn()
    except exc_type as e:
        print(f"[PASS] {name} -> correctly raised {type(e).__name__}")
    else:
        print(f"[FAIL] {name} -> did not raise expected {exc_type.__name__}")
        assert False, f"{name}: did not raise {exc_type.__name__}"


class _FakeResponse:
    def __init__(self, payload, status_ok=True):
        self._payload = payload
        self._status_ok = status_ok

    def raise_for_status(self):
        if not self._status_ok:
            raise requests.exceptions.HTTPError("boom")

    def json(self):
        return self._payload


def test_call_local_model_posts_expected_shape():
    original = requests.post
    captured = {}

    def fake_post(url, json=None, timeout=None):
        captured["url"] = url
        captured["json"] = json
        captured["timeout"] = timeout
        return _FakeResponse({"message": {"content": "hello"}})

    requests.post = fake_post
    try:
        result = call_local_model("system prompt", "user text", "some-model", timeout=30)
        expect("返回模型回答的纯文字", result == "hello")
        expect("请求打到 OLLAMA_HOST 的 /api/chat", captured["url"] == f"{local_llm_client.OLLAMA_HOST}/api/chat")
        expect("model_id 透传给请求体", captured["json"]["model"] == "some-model")
        expect("system/user 两条消息都在里面",
               [m["role"] for m in captured["json"]["messages"]] == ["system", "user"])
        expect("timeout 透传", captured["timeout"] == 30)
    finally:
        requests.post = original


def test_call_local_model_raises_on_http_error():
    original = requests.post
    requests.post = lambda url, json=None, timeout=None: _FakeResponse({}, status_ok=False)
    try:
        expect_raises(
            "Ollama 返回错误状态码时抛 HTTPError",
            requests.exceptions.HTTPError,
            lambda: call_local_model("sys", "user", "model-x"),
        )
    finally:
        requests.post = original


def test_local_extract_uses_default_model_and_embeds_schema():
    original = local_llm_client.call_local_model
    calls = {}

    def fake_call_local_model(system_prompt, user_text, model_id, timeout=300):
        calls["system_prompt"] = system_prompt
        calls["user_text"] = user_text
        calls["model_id"] = model_id
        return "extracted"

    local_llm_client.call_local_model = fake_call_local_model
    try:
        result = local_extract("some travel blog text")
        expect("返回底层调用的结果", result == "extracted")
        expect("没传 model_id 时用默认抽取模型", calls["model_id"] == local_llm_client.DEFAULT_EXTRACT_MODEL)
        expect("原文本原样传给模型", calls["user_text"] == "some travel blog text")
        expect("system prompt 里嵌入了 JSON Schema", '"type"' in calls["system_prompt"])
    finally:
        local_llm_client.call_local_model = original


def test_local_extract_respects_explicit_model_id():
    original = local_llm_client.call_local_model
    calls = {}
    local_llm_client.call_local_model = lambda sp, ut, model_id, timeout=300: calls.setdefault("model_id", model_id) or "ok"
    try:
        local_extract("text", model_id="custom-model")
        expect("显式传入的 model_id 会覆盖默认值", calls["model_id"] == "custom-model")
    finally:
        local_llm_client.call_local_model = original


if __name__ == "__main__":
    test_call_local_model_posts_expected_shape()
    test_call_local_model_raises_on_http_error()
    test_local_extract_uses_default_model_and_embeds_schema()
    test_local_extract_respects_explicit_model_id()
