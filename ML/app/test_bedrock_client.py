"""
自测脚本：bedrock_client.py（真实 Bedrock 模型客户端）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_bedrock_client.py
不需要联网/AWS 凭证/Model access：boto3.client 和跨账号 assume-role 都在
测试里被替换成假对象，只测 bedrock_client 自己拼请求/解析响应/懒加载客户端
的逻辑。
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import bedrock_client  # noqa: E402
from bedrock_client import _strip_code_fence, bedrock_extract, call_bedrock_model  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


def test_strip_code_fence_removes_json_fence():
    raw = '```json\n{"a": 1}\n```'
    expect("去掉 ```json 代码块", _strip_code_fence(raw) == '{"a": 1}')


def test_strip_code_fence_removes_bare_fence():
    raw = '```\n{"a": 1}\n```'
    expect("去掉不带语言标注的代码块", _strip_code_fence(raw) == '{"a": 1}')


def test_strip_code_fence_leaves_plain_json_untouched():
    raw = '{"a": 1}'
    expect("本来就没代码块的原样返回", _strip_code_fence(raw) == '{"a": 1}')


class _FakeBedrockClient:
    def __init__(self, response_text):
        self.response_text = response_text
        self.calls = []

    def converse(self, **kwargs):
        self.calls.append(kwargs)
        return {"output": {"message": {"content": [{"text": self.response_text}]}}}


def test_call_bedrock_model_uses_default_model_id_and_strips_fence():
    original_client, original_get_client = bedrock_client._client, bedrock_client._get_client
    fake = _FakeBedrockClient('```json\n{"ok": true}\n```')
    bedrock_client._get_client = lambda: fake
    try:
        result = call_bedrock_model("system prompt", "user text")
        expect("返回值去掉了代码块", result == '{"ok": true}')
        expect("没传 model_id 时用默认 MODEL_ID", fake.calls[0]["modelId"] == bedrock_client.MODEL_ID)
        expect("system/messages 按 Bedrock converse 的形状拼装",
               fake.calls[0]["system"] == [{"text": "system prompt"}]
               and fake.calls[0]["messages"][0]["content"][0]["text"] == "user text")
    finally:
        bedrock_client._client, bedrock_client._get_client = original_client, original_get_client


def test_call_bedrock_model_respects_explicit_model_id():
    original_get_client = bedrock_client._get_client
    fake = _FakeBedrockClient("plain text")
    bedrock_client._get_client = lambda: fake
    try:
        call_bedrock_model("sys", "user", model_id="custom-model-id", max_tokens=500)
        expect("显式传入的 model_id 会覆盖默认值", fake.calls[0]["modelId"] == "custom-model-id")
        expect("max_tokens 透传到 inferenceConfig", fake.calls[0]["inferenceConfig"]["maxTokens"] == 500)
    finally:
        bedrock_client._get_client = original_get_client


def test_bedrock_extract_embeds_schema_and_uses_model_id():
    original = bedrock_client.call_bedrock_model
    calls = {}

    def fake_call_bedrock_model(system_prompt, user_text, model_id):
        calls["system_prompt"] = system_prompt
        calls["user_text"] = user_text
        calls["model_id"] = model_id
        return "extracted"

    bedrock_client.call_bedrock_model = fake_call_bedrock_model
    try:
        result = bedrock_extract("some travel blog text", source_name="sample_1.txt")
        expect("返回底层调用的结果", result == "extracted")
        expect("用的是当前配置的 MODEL_ID", calls["model_id"] == bedrock_client.MODEL_ID)
        expect("原文本原样传给模型", calls["user_text"] == "some travel blog text")
        expect("system prompt 里嵌入了 JSON Schema", '"type"' in calls["system_prompt"])
    finally:
        bedrock_client.call_bedrock_model = original


def test_get_client_is_cached_across_calls():
    original_client, original_boto3_client = bedrock_client._client, __import__("boto3").client
    bedrock_client._client = None
    created = {"count": 0}

    def fake_boto3_client(service_name, region_name=None):
        created["count"] += 1
        return object()

    import boto3
    boto3.client = fake_boto3_client
    try:
        first = bedrock_client._get_client()
        second = bedrock_client._get_client()
        expect("同一个客户端实例被复用", first is second)
        expect("boto3.client 只被真正调用一次", created["count"] == 1)
    finally:
        bedrock_client._client = original_client
        boto3.client = original_boto3_client


def test_get_client_uses_assume_role_when_configured():
    original_client = bedrock_client._client
    original_arn = bedrock_client.ASSUME_ROLE_ARN
    original_assume = bedrock_client._assume_role_session
    bedrock_client._client = None
    bedrock_client.ASSUME_ROLE_ARN = "arn:aws:iam::123456789012:role/cross-account"
    calls = {}

    class _FakeSession:
        def client(self, service_name, region_name=None):
            calls["region_name"] = region_name
            return "assumed-role-client"

    def fake_assume_role_session(role_arn):
        calls["role_arn"] = role_arn
        return _FakeSession()

    bedrock_client._assume_role_session = fake_assume_role_session
    try:
        client = bedrock_client._get_client()
        expect("配置了 ASSUME_ROLE_ARN 时走跨账号 session", client == "assumed-role-client")
        expect("assume role 的 ARN 被正确传入", calls["role_arn"] == bedrock_client.ASSUME_ROLE_ARN)
    finally:
        bedrock_client._client = original_client
        bedrock_client.ASSUME_ROLE_ARN = original_arn
        bedrock_client._assume_role_session = original_assume


if __name__ == "__main__":
    test_strip_code_fence_removes_json_fence()
    test_strip_code_fence_removes_bare_fence()
    test_strip_code_fence_leaves_plain_json_untouched()
    test_call_bedrock_model_uses_default_model_id_and_strips_fence()
    test_call_bedrock_model_respects_explicit_model_id()
    test_bedrock_extract_embeds_schema_and_uses_model_id()
    test_get_client_is_cached_across_calls()
    test_get_client_uses_assume_role_when_configured()
