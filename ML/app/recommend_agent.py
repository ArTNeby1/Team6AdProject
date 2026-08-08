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

from content_recommender import recommend_from_dataset
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
    # description 一律英文：会被序列化进 system prompt 喂给模型，理由同 trip_models.py
    name: str = Field(max_length=255, description="Name of the recommended place")
    type: Literal["attraction", "restaurant", "hotel", "market", "other"] = Field(
        description="Place category, same enum as the extraction stage"
    )
    # 上限对齐后端 draft_place.note VARCHAR(255)——推荐理由如果要落库就是存这一列，
    # 不限长的话模型写嗨了会在后端插入时才炸。
    reason: str = Field(
        max_length=255,
        description="Why this place suits the traveller, grounded in the places and "
        "preferences given in the input. Never invent places.",
    )
    activities: list[str] = Field(
        default_factory=list, description="Suggested things to do at this place"
    )


class RecommendationResult(BaseModel):
    destination: str
    based_on: list[str] = Field(
        description="Names of the already-planned places this recommendation was based on"
    )
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


# ---------------------------------------------------------------------------
# grounded 版推荐：先查真实数据集拿候选，再让 LLM 从候选里挑 + 写理由
# ---------------------------------------------------------------------------

GROUNDED_SYSTEM_PROMPT = """You are a travel recommendation assistant for Singapore.

You are given:
1. `trip_so_far`: places the traveler has already planned.
2. `candidates`: a shortlist of REAL Singapore places retrieved from an official
   dataset, each with a distance in km from the traveler's existing plan.

Pick the {top_n} best candidates and explain why each fits this traveler.

HARD RULES:
- You MUST only pick places whose `name` appears EXACTLY in `candidates`.
  Never invent a place, never rename one, never merge two.
- Copy each chosen place's `name` and `type` character-for-character from the
  candidate list.
- Base each `reason` on what the traveler already showed interest in, and
  mention proximity when the candidate is close.
- Keep each `reason` under 200 characters.

Output ONLY a single JSON object matching this JSON Schema exactly. No markdown
fences, no commentary.

JSON Schema:
{schema}
"""


def _normalize_name(name: str) -> str:
    """
    地点名归一化，用来做候选表回查。只保留字母和数字，忽略大小写、空格、标点。

    为什么不用完全相等：数据集里的官方名字带商标符号，比如
    'Marina Bay Sands® SkyPark'，模型抄写时几乎一定会把 ® 丢掉，
    严格相等会把这种"选对了地方只是少个符号"的结果误判成编造，实测就误杀过。

    归一化之后仍然拦得住真正的编造——模型要是凭空说一个数据集里没有的景点，
    归一化后照样匹配不上任何候选。放宽的只是写法差异，不是地点本身。
    """
    return "".join(ch.lower() for ch in name if ch.isalnum())


def recommend_grounded(
    trip_extraction: dict,
    preference_text: str | None = None,
    top_n: int = 3,
    candidate_pool: int = 10,
    mode: str = "hybrid",
    max_distance_km: float | None = None,
    model_id: str | None = None,
) -> list[dict]:
    """
    F-18 要的"真·推荐"：地点来自真实数据集（保证存在、带真实经纬度），
    推荐理由由 LLM 针对这个用户写（保证不是干巴巴的相似度数字）。

    跟 recommend_places() 的区别——这是这次改造的核心，答辩会被问：
      recommend_places()   : 直接问 LLM "推荐几个地方"，地点是模型凭记忆编的，
                             可能推荐出不存在/已停业的地方，也没有坐标。
      recommend_grounded() : 先用 content_recommender 从 107 个真实景点里检索出
                             candidate_pool 个候选，LLM 只能在这个名单里挑。
                             模型负责"选哪个、为什么"，不负责"有哪些地方"。

    这样即使模型胡说，最坏情况也只是理由写得牵强，不会凭空造出一个景点。

    返回 list[dict]，每条 = LLM 给的 name/type/reason/activities
    + 数据集给的 lat/lng/distance_km/similarity（坐标永远以数据集为准，不信模型）。
    """
    candidates = recommend_from_dataset(
        trip_extraction,
        preference_text=preference_text,
        top_n=candidate_pool,
        mode=mode,
        max_distance_km=max_distance_km,
    )
    if not candidates:
        return []

    # 只把模型需要的字段喂进去：地址/相似度分数对写推荐理由没帮助，反而占 token
    candidate_brief = [
        {"name": c["name"], "type": c["type"], "distance_km": c["distance_km"]}
        for c in candidates
    ]
    by_name = {_normalize_name(c["name"]): c for c in candidates}

    if RECOMMEND_PROVIDER == "mock":
        # mock 模式：跳过 LLM 那一步，直接返回数据集检索出来的前 top_n 条。
        # 注意推荐结果本身还是**真的**——地点、坐标、距离都来自真实数据集，
        # 因为 content_recommender 是纯 TF-IDF，本来就不需要模型。
        # 假的只有 reason（换成模板句），因为写理由才是 LLM 干的活。
        # 给后端联调用：他们本机不装 Ollama 也能拿到结构完全正确的响应。
        return [
            {
                "name": c["name"],
                "type": c["type"],
                "lat": c["lat"],
                "lng": c["lng"],
                "distance_km": c["distance_km"],
                "similarity": c["similarity"],
                "reason": (
                    f"[MOCK] Similar to your planned places"
                    + (f", {c['distance_km']}km away" if c["distance_km"] is not None else "")
                ),
                "activities": [],
            }
            for c in candidates[:top_n]
        ]

    system_prompt = GROUNDED_SYSTEM_PROMPT.format(
        top_n=top_n,
        schema=json.dumps(RecommendationResult.model_json_schema(), ensure_ascii=False),
    )
    user_payload = {"trip_so_far": trip_extraction, "candidates": candidate_brief}
    if preference_text:
        user_payload["preferences"] = preference_text

    if RECOMMEND_PROVIDER == "bedrock":
        from bedrock_client import call_bedrock_model  # 延迟导入：ollama 路径不强制装 boto3

        raw_text = call_bedrock_model(
            system_prompt, json.dumps(user_payload, ensure_ascii=False),
            model_id or DEFAULT_RECOMMEND_MODEL_BEDROCK,
        )
    else:
        raw_text = call_local_model(
            system_prompt, json.dumps(user_payload, ensure_ascii=False),
            model_id or DEFAULT_RECOMMEND_MODEL,
        )

    llm_result = RecommendationResult.model_validate(json.loads(raw_text))

    # 关键一步：拿模型返回的名字回查候选表，对不上的直接丢掉。
    # 提示词里已经要求"只能从候选里挑"，但提示词是请求不是保证，这里才是真的拦截。
    grounded = []
    for place in llm_result.recommended:
        source = by_name.get(_normalize_name(place.name))
        if source is None:
            print(f"[grounded] dropped: not in candidate list, treated as invented: {place.name!r}")
            continue
        grounded.append(
            {
                "name": source["name"],
                "type": source["type"],
                "lat": source["lat"],
                "lng": source["lng"],
                "distance_km": source["distance_km"],
                "similarity": source["similarity"],
                "reason": place.reason,
                "activities": place.activities,
            }
        )
        if len(grounded) >= top_n:
            break
    return grounded


if __name__ == "__main__":
    # 手动冒烟测试，需要本机 Ollama 已经在跑
    demo_trip = {
        "destination": "Singapore",
        "dates": [],
        "places": [
            {"name": "Gardens by the Bay", "type": "attraction",
             "lat": 1.2816, "lng": 103.8636, "activities": ["Cloud Forest dome"]},
        ],
    }
    print("=== old version (LLM names places from memory) ===")
    print(recommend_places(demo_trip, preference_text="travel_style=culture").model_dump_json(indent=2))
    print("\n=== new grounded version (places come from the real dataset) ===")
    print(json.dumps(recommend_grounded(demo_trip, preference_text="travel_style=culture"),
                     ensure_ascii=False, indent=2))
