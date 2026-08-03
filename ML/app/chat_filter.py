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

# 过滤这一步默认用哪个模型，独立于抽取阶段的 EXTRACT_MODEL，
# 体现"多个模型分工"：这步任务简单（判断相关/不相关），可以配一个更轻量的模型，
# 不用跟抽取阶段共用同一个大模型。本机已有 llama3:latest 可以直接用。
DEFAULT_FILTER_MODEL = os.environ.get("FILTER_MODEL", "llama3:latest")

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
    return call_local_model(SYSTEM_PROMPT, transcript, model_id or DEFAULT_FILTER_MODEL).strip()


if __name__ == "__main__":
    # 手动冒烟测试，需要本机 Ollama 已经在跑
    demo_messages = [
        {"role": "user", "content": "嗨你好呀！"},
        {"role": "user", "content": "我想去新加坡玩5天，主要想看看Gardens by the Bay"},
        {"role": "assistant", "content": "好的，没问题！"},
        {"role": "user", "content": "哈哈谢谢，另外还想吃chicken rice"},
    ]
    print(filter_chat_noise(demo_messages))
