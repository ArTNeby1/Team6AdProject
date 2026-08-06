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
from recommend_agent import RecommendationResult, recommend_places  # noqa: E402
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


def run_pipeline(
    messages: list[dict] | None = None,
    raw_content: str | None = None,
    source_name: str = "api_input",
    preference_text: str | None = None,
) -> PipelineResult:
    """
    messages: 多轮聊天记录（对应 chat_message 表，[{"role": ..., "content": ...}]），
        传了就先走 chat_filter 降噪——这是"多轮完善"场景（PlanningController /refine）。
    raw_content: 单次粗略路线文本（对应 planning_session.initial_brief），
        没有 messages 时用这个，不需要过降噪这一步。
    preference_text: 可选，来自 trip_preference 表或聊天里提到的偏好，直接传给推荐阶段。
    """
    if messages:
        cleaned_text = filter_chat_noise(messages)
    else:
        cleaned_text = (raw_content or "").strip()

    if not cleaned_text:
        return PipelineResult(cleaned_text="", error="没有可抽取的有效内容（过滤后为空）")

    try:
        extraction = extract_with_retry(cleaned_text, source_name, _extract_fn)
    except ExtractionFailedError as e:
        return PipelineResult(cleaned_text=cleaned_text, error=str(e))

    recommendation = recommend_places(extraction.model_dump(), preference_text=preference_text)
    return PipelineResult(cleaned_text=cleaned_text, extraction=extraction, recommendation=recommendation)
