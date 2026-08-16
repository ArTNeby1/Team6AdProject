"""
自测脚本：chat_filter.py（聊天降噪，抽取前先只留跟行程相关的内容）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_chat_filter.py
不需要联网/Ollama/AWS：三个 provider 分支里真正会发请求的调用
（本地 Ollama / Bedrock）都被替换成假函数，只测 chat_filter 自己的路由逻辑。
"""
import os
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

# 必须在 import chat_filter 之前设：chat_filter.FILTER_PROVIDER 是 import 时读
# 环境变量决定的模块级常量，不设的话会锁死成生产默认值 "bedrock"，导致这个文件
# 一旦被 pytest 先于其它测试文件收集/导入，会让其它假定 FILTER_PROVIDER=mock
# 的测试（如 test_no_useful_content.py）跟着遭殃。跟 test_no_useful_content.py
# 顶部的说明是同一个道理。
os.environ.setdefault("FILTER_PROVIDER", "mock")

import bedrock_client  # noqa: E402
import chat_filter  # noqa: E402
from chat_filter import filter_chat_noise  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


def test_empty_messages_returns_empty_string():
    expect("空消息列表返回空字符串", filter_chat_noise([]) == "")


def test_blank_user_content_returns_empty_string():
    # 注意：这走的不是函数开头 "not transcript.strip()" 那条早退路径——
    # 因为 transcript 里每行都带 "role: " 前缀，strip 不掉，早退分支只有
    # messages 为空列表时才会命中（见 test_empty_messages_returns_empty_string）。
    # 这里测的是 mock 分支自己的行为：拼接完 user 的内容后再 strip()，
    # 全是空白的 user 消息拼完还是空字符串。
    messages = [{"role": "user", "content": "   "}]
    expect("mock 模式下 user 内容全是空白时返回空字符串", filter_chat_noise(messages) == "")


def test_mock_provider_concatenates_user_messages_only():
    original = chat_filter.FILTER_PROVIDER
    chat_filter.FILTER_PROVIDER = "mock"
    try:
        messages = [
            {"role": "user", "content": "hey there!"},
            {"role": "assistant", "content": "Sure, no problem!"},
            {"role": "user", "content": "5 days in Singapore"},
        ]
        result = filter_chat_noise(messages)
        expect("mock 模式只保留 user 说的话", result == "hey there!\n5 days in Singapore")
    finally:
        chat_filter.FILTER_PROVIDER = original


def test_bedrock_provider_delegates_to_bedrock_client():
    original_provider = chat_filter.FILTER_PROVIDER
    original_call = bedrock_client.call_bedrock_model
    chat_filter.FILTER_PROVIDER = "bedrock"
    calls = {}

    def fake_call_bedrock_model(system_prompt, user_text, model_id=None, max_tokens=2000):
        calls["system_prompt"] = system_prompt
        calls["user_text"] = user_text
        calls["model_id"] = model_id
        return "  cleaned via bedrock  "

    bedrock_client.call_bedrock_model = fake_call_bedrock_model
    try:
        messages = [{"role": "user", "content": "3 days in Singapore"}]
        result = filter_chat_noise(messages)
        expect("bedrock 分支返回值被 strip 干净", result == "cleaned via bedrock")
        expect("bedrock 分支用了默认模型 id", calls["model_id"] == chat_filter.DEFAULT_FILTER_MODEL_BEDROCK)
        expect("transcript 包含角色前缀", "user: 3 days in Singapore" in calls["user_text"])
    finally:
        chat_filter.FILTER_PROVIDER = original_provider
        bedrock_client.call_bedrock_model = original_call


def test_bedrock_provider_respects_explicit_model_id():
    original_provider = chat_filter.FILTER_PROVIDER
    original_call = bedrock_client.call_bedrock_model
    chat_filter.FILTER_PROVIDER = "bedrock"
    calls = {}

    def fake_call_bedrock_model(system_prompt, user_text, model_id=None, max_tokens=2000):
        calls["model_id"] = model_id
        return "ok"

    bedrock_client.call_bedrock_model = fake_call_bedrock_model
    try:
        filter_chat_noise([{"role": "user", "content": "hi"}], model_id="custom-model")
        expect("显式传入的 model_id 会覆盖默认值", calls["model_id"] == "custom-model")
    finally:
        chat_filter.FILTER_PROVIDER = original_provider
        bedrock_client.call_bedrock_model = original_call


def test_default_provider_delegates_to_local_model():
    original_provider = chat_filter.FILTER_PROVIDER
    original_call = chat_filter.call_local_model
    chat_filter.FILTER_PROVIDER = "ollama"
    calls = {}

    def fake_call_local_model(system_prompt, user_text, model_id, timeout=300):
        calls["model_id"] = model_id
        return "  cleaned via ollama  "

    chat_filter.call_local_model = fake_call_local_model
    try:
        messages = [{"role": "user", "content": "hi"}]
        result = filter_chat_noise(messages)
        expect("非 mock/bedrock 时走本地模型", result == "cleaned via ollama")
        expect("本地模型分支用了默认模型 id", calls["model_id"] == chat_filter.DEFAULT_FILTER_MODEL)
    finally:
        chat_filter.FILTER_PROVIDER = original_provider
        chat_filter.call_local_model = original_call


if __name__ == "__main__":
    test_empty_messages_returns_empty_string()
    test_blank_user_content_returns_empty_string()
    test_mock_provider_concatenates_user_messages_only()
    test_bedrock_provider_delegates_to_bedrock_client()
    test_bedrock_provider_respects_explicit_model_id()
    test_default_provider_delegates_to_local_model()
