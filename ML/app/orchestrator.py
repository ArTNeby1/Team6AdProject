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
EXTRACT_PROVIDER / RECOMMEND_PROVIDER，设成 "bedrock" 就切到任务 2 选定的
Nova Lite（见 ML/docs/model_selection.md）。

2026-08-11 起，EXTRACT_PROVIDER / FILTER_PROVIDER / RECOMMEND_PROVIDER 三个
默认值都固定成 "bedrock"，不是临时测试窗口，不会自动回退到本地——Nova Lite
更快（~1.6s vs 本地 Ollama CPU 推理几十秒）、任务2选型测试里唯一 0 漏抽/0 编造，
实测调用量下成本可忽略（远低于 $1）。FILTER_PROVIDER 切 bedrock 后本机验证过
能正常过滤寒暄/保留行程相关内容。三个变量仍可各自显式设成 "ollama" / "mock"
临时切走做本地调试，但那不是常规路径。
"""
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from chat_filter import filter_chat_noise  # noqa: E402
from content_recommender import indoor_outdoor_lookup  # noqa: E402
from extraction import ExtractionFailedError, extract_with_retry  # noqa: E402
from itinerary_planner import plan_multi_day_itinerary, plan_ordered_stops  # noqa: E402
from local_llm_client import local_extract  # noqa: E402
from recommend_agent import (  # noqa: E402
    RecommendationResult,
    recommend_grounded,
    recommend_places,
)
from trip_models import TripExtraction  # noqa: E402
from vague_place import resolve_vague_place_suggestions  # noqa: E402

# 跟 main.py 的 EXTRACT_PROVIDER 说明一致：固定默认 Bedrock Nova Lite，
# 显式设 EXTRACT_PROVIDER=ollama / mock 才切走。
# chat_filter / recommend_agent 各自的 Provider 由它们自己的环境变量
# （FILTER_PROVIDER / RECOMMEND_PROVIDER）控制，这里不用管。
_EXTRACT_PROVIDER = os.environ.get("EXTRACT_PROVIDER", "bedrock")
if _EXTRACT_PROVIDER == "bedrock":
    from bedrock_client import bedrock_extract  # noqa: E402

    _extract_fn = bedrock_extract
elif _EXTRACT_PROVIDER == "mock":
    from mock_client import mock_extract  # noqa: E402

    _extract_fn = mock_extract
else:
    _extract_fn = local_extract


@dataclass
class PipelineResult:
    cleaned_text: str
    extraction: Optional[TripExtraction] = None
    recommendation: Optional[RecommendationResult] = None
    error: Optional[str] = None
    # 恒定跟 error 同时出现/同时缺席，拆成单独字段是为了让 main.py 能区分
    # "用户输入没有可用信息"（NO_USEFUL_CONTENT，该让前端提示重新输入）
    # 和"抽取本身出故障"（EXTRACTION_FAILED，真正的 502），不用去解析 error
    # 那句人话文本才能分支。同样的做法已经在 needs_duration_input 上用过一次——
    # "这时候该怎么办"钉死在 AI 侧，不让下游各自猜。
    error_code: Optional[str] = None


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
        # 多轮聊天场景下最常见的"废话"：chat_filter 把寒暄/无关内容滤成了空字符串。
        # 用 error_code 而不是让下游解析 error 文本——见 PipelineResult.error_code 说明。
        return PipelineResult(
            cleaned_text="",
            error="no extractable content (empty after noise filtering)",
            error_code="NO_USEFUL_CONTENT",
        )

    try:
        extraction = extract_with_retry(cleaned_text, source_name, _extract_fn)
    except ExtractionFailedError as e:
        # 模型连续 MAX_ATTEMPTS 次都没能给出合规 JSON——这是抽取本身出故障
        # （比如格式一直错、枚举值一直不对），不是"用户没说有用的东西"，
        # 该保持 502，让后端当成真正的服务异常去查。
        return PipelineResult(cleaned_text=cleaned_text, error=str(e), error_code="EXTRACTION_FAILED")

    if not extraction.places:
        # 单次粗略路线场景（/extract-travel-info 不经过 chat_filter）下最常见的"废话"：
        # 文本非空、格式也合规，但模型老老实实抽出了 places=[]——trip_models.places
        # 2026-08-14 起允许空列表就是为了让这种情况一次成功返回，而不是逼模型编地点
        # 或者白白重试 3 次。这里跟上面"过滤后为空"走同一个 error_code，前端不用
        # 关心具体是哪个入口触发的，统一提示"请输入有效的旅行信息"就行。
        return PipelineResult(
            cleaned_text=cleaned_text,
            extraction=extraction,
            error="no destination or place found in the text",
            error_code="NO_USEFUL_CONTENT",
        )

    return PipelineResult(cleaned_text=cleaned_text, extraction=extraction)


def run_recommendation(
    places: list[dict],
    destination: str = "Singapore",
    preference_text: str | None = None,
    top_n: int = 3,
    mode: str = "hybrid",
    max_distance_km: float | None = None,
    target_date: str | None = None,
) -> dict:
    """
    第二阶段：用户确认地点之后才调。做两件事，对应返回的两个 key：

    1. `ordered_stops` —— 把**用户自己确认的**地点重排（itinerary_planner）：
       下雨的时段排室内、不下雨排室外，同时段内按距离串线路。
       没给 target_date 或天气查不到时，退化成纯按距离排。
    2. `suggested_additions` —— 推荐**用户没提过的**新地点，两部分拼在一起：
       - recommend_grounded()：候选来自真实数据集，LLM 从候选里挑并写理由；
       - resolve_vague_place_suggestions()：places 里如果有"夜市"这种笼统
         类别名（不是具体地点），换成数据集里真实存在的具体候选（见
         vague_place.py）。两部分字段形状完全一样，前端不用区分处理，
         在同一块"推荐加入"列表里勾选就行。

    places: 用户确认后的地点，形状跟抽取结果的 places 一致。
        带 lat/lng 时才能算距离（"nearby"）和串线路，没有就自动退回纯文本相似度。
        抽取阶段的 coords 是 null，坐标要等后端地理编码补上再传进来。
    """
    trip = {"destination": destination, "places": places}

    suggested = recommend_grounded(
        trip,
        preference_text=preference_text,
        top_n=top_n,
        mode=mode,
        max_distance_km=max_distance_km,
    )
    already_suggested = {s["name"].strip().lower() for s in suggested}
    for vague_suggestion in resolve_vague_place_suggestions(places, top_n=top_n):
        # 去重：避免 recommend_grounded 和笼统类别匹配都选中同一个地点时重复展示。
        if vague_suggestion["name"].strip().lower() not in already_suggested:
            suggested.append(vague_suggestion)
            already_suggested.add(vague_suggestion["name"].strip().lower())

    # 传进 planner 的是副本：plan_ordered_stops 会往 dict 里塞内部用的 _io 字段，
    # 不复制的话会污染调用方传进来的原始数据。
    ordered, forecast = plan_ordered_stops(
        [dict(p) for p in places],
        target_date=target_date,
        dataset_lookup=indoor_outdoor_lookup(),
    )

    return {
        "weather_summary": forecast["summary"] if forecast else None,
        "ordered_stops": ordered,
        "suggested_additions": suggested,
    }


def run_daily_itinerary(
    places: list[dict],
    start_date: str | None = None,
    num_days: int = 1,
) -> dict:
    """
    F-09：把用户确认的地点按天排成行程。跟 run_recommendation() 平级的第三个入口。

    跟 run_recommendation() 里 `ordered_stops` 的区别，一句话：
      run_recommendation  -> **一天**之内怎么排（还顺带推荐新地点）
      run_daily_itinerary -> **多天**怎么分（第 1 天去哪片、第 2 天去哪片），
                             每一天内部照样调同一套单天排序逻辑

    所以这个接口**不做推荐** —— 推荐是 /recommend 的事，两个接口各管一件事，
    前端要"排程 + 推荐"就分别调，不把两件事绑死（跟当初拆 extract/recommend 同一个理由）。

    places: 用户确认后的地点，形状跟 run_recommendation() 的一致。
        带 lat/lng 才能按区域分天；没坐标的会被排到最后几天（不报错）。
    num_days 越界、start_date 格式不对时，plan_multi_day_itinerary 会抛 ValueError，
        由 main.py 转成 400 返回给调用方。

    这里不用像 run_recommendation() 那样手动复制 places：
    plan_multi_day_itinerary 内部已经复制过了。
    """
    days = plan_multi_day_itinerary(
        places,
        start_date=start_date,
        num_days=num_days,
        dataset_lookup=indoor_outdoor_lookup(),
    )
    return {"days": days}


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
