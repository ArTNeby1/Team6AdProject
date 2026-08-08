"""
把三个 agent 串成一条链：

  chat_filter（降噪，过滤掉用户说的废话）
        -> local_llm_client（抽取成 TripExtraction JSON）
        -> recommend_agent（分析 + 推荐旅游地点）

对应 `backend/src/main/java/.../client/AiPlanningClient.java` 里
`extractTravelInfo(rawContent, sourceUrl)` 这条契约：main.py 的
`/extract-travel-info`、`/refine` 两个接口直接调这里的 run_pipeline()。

只有 run_pipeline() 最终返回的 recommendation 部分，才是设计上要传给后端的
结果——中间的 cleaned_text / extraction 只在 Python 这边内部流转，后端不需要
关心，也不需要它们的字段跟 destination/draft_place 表对齐。

三个阶段分别用哪个模型（"多模型分工"），由各自模块的环境变量单独控制：
FILTER_MODEL（chat_filter.py）/ EXTRACT_MODEL（local_llm_client.py）/
RECOMMEND_MODEL（recommend_agent.py）。互不影响，换个环境变量就能让某一阶段
单独换模型，不用改这份编排代码。

三个阶段各自还能独立选"本地 Ollama 还是 Bedrock"：FILTER_PROVIDER /
EXTRACT_PROVIDER / RECOMMEND_PROVIDER，默认都是 "ollama"，设成 "bedrock" 就切
到任务 2 选定的 Nova Lite（见 ML/docs/model_selection.md）。
"""
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from chat_filter import filter_chat_noise  # noqa: E402
from extraction import ExtractionFailedError, extract_with_retry  # noqa: E402
from local_llm_client import local_extract  # noqa: E402
from recommend_agent import (  # noqa: E402
    RecommendationResult,
    recommend_grounded,
    recommend_places,
)
from trip_models import TripExtraction  # noqa: E402

# 跟 main.py 的 EXTRACT_PROVIDER 说明一致：默认本地 Ollama，设
# EXTRACT_PROVIDER=bedrock 切到任务 2 选定的 Bedrock Nova Lite。
# chat_filter / recommend_agent 各自的 Provider 由它们自己的环境变量
# （FILTER_PROVIDER / RECOMMEND_PROVIDER）控制，这里不用管。
_EXTRACT_PROVIDER = os.environ.get("EXTRACT_PROVIDER", "ollama")
if _EXTRACT_PROVIDER == "bedrock":
    from bedrock_client import bedrock_extract  # noqa: E402

    _extract_fn = bedrock_extract
else:
    _extract_fn = local_extract


@dataclass
class PipelineResult:
    cleaned_text: str
    extraction: Optional[TripExtraction] = None
    recommendation: Optional[RecommendationResult] = None
    error: Optional[str] = None


def run_extraction(
    messages: list[dict] | None = None,
    raw_content: str | None = None,
    source_name: str = "api_input",
) -> PipelineResult:
    """
    第一阶段：降噪 + 抽取，**不做推荐**。

    为什么单独拆出来（这是这次改造的核心）：产品流程是"解析 -> 给用户确认 ->
    确认后才推荐"，推荐必须发生在用户点确认之后。原来的 run_pipeline() 把两步
    绑在一起一次跑完，等于用户还没确认就先推荐了，既跟流程不符，也白烧一次
    LLM 调用（用户完全可能把地点删掉重来）。

    messages: 多轮聊天记录（对应 chat_message 表），传了就先走 chat_filter 降噪。
    raw_content: 单次粗略路线文本（对应 planning_session.initial_brief），
        没有 messages 时用这个，不需要过降噪这一步。
    """
    if messages:
        cleaned_text = filter_chat_noise(messages)
    else:
        cleaned_text = (raw_content or "").strip()

    if not cleaned_text:
        return PipelineResult(cleaned_text="", error="no extractable content (empty after noise filtering)")

    try:
        extraction = extract_with_retry(cleaned_text, source_name, _extract_fn)
    except ExtractionFailedError as e:
        return PipelineResult(cleaned_text=cleaned_text, error=str(e))

    return PipelineResult(cleaned_text=cleaned_text, extraction=extraction)


def run_recommendation(
    places: list[dict],
    destination: str = "Singapore",
    preference_text: str | None = None,
    top_n: int = 3,
    mode: str = "hybrid",
    max_distance_km: float | None = None,
) -> list[dict]:
    """
    第二阶段：用户确认地点之后才调，产出推荐（F-18）。

    走 recommend_grounded()：候选地点来自真实数据集（107 个新加坡景点，带真实
    经纬度），LLM 只负责从候选里挑并写推荐理由，不负责"有哪些地方"——所以不会
    推荐出不存在的景点。详见 recommend_agent.recommend_grounded 的说明。

    places: 用户确认后的地点，形状跟抽取结果的 places 一致。
        带 lat/lng 时才能算距离（"nearby"），没有就自动退回纯文本相似度。
        抽取阶段的 coords 是 null，坐标要等后端地理编码补上再传进来。
    """
    trip = {"destination": destination, "places": places}
    return recommend_grounded(
        trip,
        preference_text=preference_text,
        top_n=top_n,
        mode=mode,
        max_distance_km=max_distance_km,
    )


def run_pipeline(
    messages: list[dict] | None = None,
    raw_content: str | None = None,
    source_name: str = "api_input",
    preference_text: str | None = None,
) -> PipelineResult:
    """
    【已弃用，保留只为兼容】一次跑完抽取 + 推荐的老入口。

    生产链路不要再用：它在用户确认之前就把推荐做了，跟"解析 -> 确认 -> 推荐"
    的流程不符（见 run_extraction 的说明）。新代码请分别调 run_extraction()
    和 run_recommendation()。

    这里用的还是老的 recommend_places()（LLM 凭记忆编地点），不是 grounded 版，
    留着是为了对比两种推荐方式的效果差异（任务 2 的模型选型文档会用到）。
    """
    result = run_extraction(messages=messages, raw_content=raw_content, source_name=source_name)
    if result.error:
        return result

    result.recommendation = recommend_places(
        result.extraction.model_dump(), preference_text=preference_text
    )
    return result
