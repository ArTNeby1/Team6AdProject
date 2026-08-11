"""
第一个 agent：聊天降噪（对应任务里说的"过滤掉用户说的废话"）。

背景：按 `Documents/Shared Documents/DATA_DICTIONARY_zh.md` 的产品路径，用户是
"给一个粗略路线 -> 跟 AI Agent 多轮聊天完善"，聊天记录存在 `chat_message` 表
（role: user/assistant/system）。这些对话里夹杂大量寒暄、跟行程无关的内容
（"你好""谢谢""哈哈"之类），如果原样丢给抽取 agent，会拉低抽取准确率、
也浪费 token。所以在抽取之前先过一道"只留跟行程规划相关内容"的关卡。

输出是一段纯文字（不是 JSON）——过滤后的文字会被当成 extract 阶段的输入，
接口形状因此保持跟 sample_*.txt 一样（一段文本进，一段文本出），extract 阶段
不用关心上游是"一篇博客"还是"一段过滤后的聊天记录"。
"""
import os

from local_llm_client import call_local_model

# 这一步走本地 Ollama 还是 Bedrock，独立于抽取阶段的 EXTRACT_PROVIDER——
# 三段各自能单独选，参考 main.py/orchestrator.py 里 EXTRACT_PROVIDER 的说明。
# 2026-08-11：默认切到 "bedrock"，跟 EXTRACT_PROVIDER/RECOMMEND_PROVIDER 的
# 测试窗口默认保持一致（同一个 Nova Lite 模型，同一份 AWS 凭证），本机验证过
# 能正常过滤寒暄/保留行程相关内容。想切回本地就显式设 FILTER_PROVIDER=ollama。
FILTER_PROVIDER = os.environ.get("FILTER_PROVIDER", "bedrock")

# 过滤这一步默认用哪个模型，独立于抽取阶段的 EXTRACT_MODEL，
# 体现"多个模型分工"：这步任务简单（判断相关/不相关），可以配一个更轻量的模型，
# 不用跟抽取阶段共用同一个大模型。本机已有 llama3:latest 可以直接用。
DEFAULT_FILTER_MODEL = os.environ.get("FILTER_MODEL", "llama3:latest")
# FILTER_PROVIDER=bedrock 时用这个模型 id，不传就是 Nova Lite（跟抽取阶段选型一致）
DEFAULT_FILTER_MODEL_BEDROCK = os.environ.get("FILTER_MODEL_BEDROCK", "amazon.nova-lite-v1:0")

SYSTEM_PROMPT = """You clean up a travel-planning chat transcript before it is
handed to a downstream extraction step.

Given the conversation below, output ONLY the parts that are relevant to trip
planning: destinations, dates, places, activities, preferences. Drop greetings,
small talk, thanks, and anything unrelated to the trip. Keep the surviving
content as plain text in the original language, roughly in chronological
order. Do not summarize, invent, or add anything that wasn't said. If nothing
relevant survives, output an empty string.

Do not output JSON, markdown, or commentary — just the cleaned plain text.
"""


def filter_chat_noise(messages: list[dict], model_id: str | None = None) -> str:
    """
    messages: [{"role": "user"|"assistant"|"system", "content": "..."}]，
    跟 chat_message 表的字段名保持一致，orchestrator.py 直接把聊天记录传进来即可。
    返回：过滤后的纯文字，供 extract 阶段使用。
    """
    transcript = "\n".join(f"{m['role']}: {m['content']}" for m in messages)
    if not transcript.strip():
        return ""
    if FILTER_PROVIDER == "mock":
        # mock 模式：不调任何模型，直接把 user 说的话拼起来当"过滤后的文字"。
        # 给后端联调用——他们本机不用装 Ollama 也能把 HTTP 那一层调通，见 main.py 顶部说明。
        return "\n".join(m["content"] for m in messages if m.get("role") == "user").strip()
    if FILTER_PROVIDER == "bedrock":
        from bedrock_client import call_bedrock_model  # 延迟导入：ollama 路径不强制要求装 boto3/配好 AWS 凭证

        return call_bedrock_model(SYSTEM_PROMPT, transcript, model_id or DEFAULT_FILTER_MODEL_BEDROCK).strip()
    return call_local_model(SYSTEM_PROMPT, transcript, model_id or DEFAULT_FILTER_MODEL).strip()


if __name__ == "__main__":
    # 手动冒烟测试，需要本机 Ollama 已经在跑
    # 测试数据一律用英文：实测本地 8B 量化模型对中文输入会吐乱码地名
    # （见 ML/docs/ai_contract.md 第 8 节），英文正常。demo 用中文会误导人
    # 以为流程有问题，其实是模型语言能力问题。
    demo_messages = [
        {"role": "user", "content": "hey there!"},
        {"role": "user", "content": "I want to spend 5 days in Singapore, mainly to see Gardens by the Bay"},
        {"role": "assistant", "content": "Sure, no problem!"},
        {"role": "user", "content": "haha thanks, also I'd like to try chicken rice"},
    ]
    print(filter_chat_noise(demo_messages))
