"""
第二个 agent：分析已抽取的地点 + 用户偏好，推荐旅游地点（对应任务里说的
"分析推荐旅游地点"）。

链路位置：chat_filter（降噪）-> local_llm_client（抽取出 TripExtraction）->
本文件（推荐）。只有这一步的结果（RecommendationResult）才是最终要传给
后端的东西——中间两步的输出都只在 Python 这边内部流转，不直接进后端，
呼应"JSON 的表不用跟后端对应"这条要求。

输出字段特意贴近 `draft_place` 需要的样子（name/category/note），
但不产 `address`/`latitude`/`longitude`——那两个字段按
`ML/docs/backend_alignment_brief.md` 问题 2 的结论，由后端 `MapPlacesClient`
地理编码之后再补，不该也不能由本地模型编。
"""
import json
import os
from typing import Literal, Optional

from pydantic import BaseModel, Field

from local_llm_client import call_local_model

# 这一步走本地 Ollama 还是 Bedrock，独立于其它两段，参考 main.py 里
# EXTRACT_PROVIDER 的说明。默认 "ollama"，显式设 RECOMMEND_PROVIDER=bedrock 才切。
RECOMMEND_PROVIDER = os.environ.get("RECOMMEND_PROVIDER", "ollama")

# 推荐这一步默认用哪个模型，独立于 EXTRACT_MODEL/FILTER_MODEL，同样是"多模型分工"的一环：
# 推荐需要一点"发散 + 解释理由"的能力，可以换一个跟抽取阶段不同的模型试试效果。
DEFAULT_RECOMMEND_MODEL = os.environ.get("RECOMMEND_MODEL", "llama3.1:8b-instruct-q4_K_M")
# RECOMMEND_PROVIDER=bedrock 时用这个模型 id，不传就是 Nova Lite
DEFAULT_RECOMMEND_MODEL_BEDROCK = os.environ.get("RECOMMEND_MODEL_BEDROCK", "amazon.nova-lite-v1:0")


class RecommendedPlace(BaseModel):
    name: str = Field(description="推荐的地点名称")
    type: Literal["attraction", "restaurant", "hotel", "market", "other"] = Field(
        description="地点类型，跟抽取阶段的枚举保持一致"
    )
    reason: str = Field(description="推荐理由，必须能从输入的已抽取地点/偏好里找到依据，不许瞎编景点")
    activities: list[str] = Field(default_factory=list, description="建议在这个地点做的事情")


class RecommendationResult(BaseModel):
    destination: str
    based_on: list[str] = Field(description="这次推荐参考了哪些已抽取的地点名称")
    recommended: list[RecommendedPlace] = Field(default_factory=list)


RECOMMEND_SCHEMA = RecommendationResult.model_json_schema()

SYSTEM_PROMPT = """You are a travel recommendation assistant. You are given a
JSON object describing places a traveler has already mentioned for a trip
(destination, dates, places already planned), and optionally their stated
preferences (travel style, preferred transport).

Suggest 2-5 ADDITIONAL places (not already in the input places list) that fit
the same destination and match the traveler's apparent interests. Every
suggestion must be a real, well-known place for that destination — do not
invent places. Explain the reason for each suggestion in terms of what the
traveler already showed interest in.

Output ONLY a single JSON object matching this JSON Schema exactly. No
markdown fences, no commentary.

JSON Schema:
{schema}
"""


def recommend_places(
    trip_extraction: dict,
    preference_text: Optional[str] = None,
    model_id: str | None = None,
) -> RecommendationResult:
    """
    trip_extraction: 符合 trip_schema.json 的 dict（destination/dates/places）。
    preference_text: 可选，来自 trip_preference 表的偏好（travel_style /
        prefer_transport），或者聊天里提到的口味偏好，直接拼进 user 消息里。
    """
    system_prompt = SYSTEM_PROMPT.format(schema=json.dumps(RECOMMEND_SCHEMA, ensure_ascii=False))

    user_payload = {"trip_so_far": trip_extraction}
    if preference_text:
        user_payload["preferences"] = preference_text
    user_content = json.dumps(user_payload, ensure_ascii=False)

    if RECOMMEND_PROVIDER == "bedrock":
        from bedrock_client import call_bedrock_model  # 延迟导入：ollama 路径不强制要求装 boto3/配好 AWS 凭证

        raw_text = call_bedrock_model(system_prompt, user_content, model_id or DEFAULT_RECOMMEND_MODEL_BEDROCK)
    else:
        raw_text = call_local_model(system_prompt, user_content, model_id or DEFAULT_RECOMMEND_MODEL)
    return RecommendationResult.model_validate(json.loads(raw_text))


if __name__ == "__main__":
    # 手动冒烟测试，需要本机 Ollama 已经在跑
    demo_trip = {
        "destination": "Singapore",
        "dates": [],
        "places": [
            {"name": "Gardens by the Bay", "type": "attraction", "coords": None,
             "activities": ["Cloud Forest dome"]},
        ],
    }
    result = recommend_places(demo_trip, preference_text="travel_style=culture, prefer_transport=walk")
    print(result.model_dump_json(indent=2))
