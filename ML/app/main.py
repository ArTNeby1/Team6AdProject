"""
FastAPI 入口。两组接口：

1. `/extract` —— 老接口，保留用于本地调试/校验单篇文本，直接走 EXTRACT_FN
   （mock / bedrock / 本地 Ollama 三选一，见下方）。
2. `/extract-travel-info`、`/refine` —— 对齐后端 `AiPlanningClient` 契约
   （见 `backend/.../client/AiPlanningClient.java`）的接口，走完整的三段式
   pipeline：chat_filter（降噪）-> 本地模型抽取 -> recommend_agent（推荐），
   由 `orchestrator.run_pipeline()` 编排，返回的结果才是要传给后端的东西。

本地启动方式（在项目根目录 Team6AdProject 下执行）：
    uvicorn main:app --reload --app-dir ML/app --port 8001
启动后访问 http://127.0.0.1:8001/docs 能看到自动生成的交互式测试页面。

2026-08-11 起，EXTRACT_PROVIDER=FILTER_PROVIDER=RECOMMEND_PROVIDER=bedrock 是
固定默认值（不是临时测试窗口，不会自动回退到本地）——本地 Ollama 在这台开发机
上是 CPU 推理，单次调用要几十秒，本机装 Ollama 也不需要，走 AWS 就够快。
需要能访问 Bedrock 的 AWS 凭证（当前借用 gf-temp profile，见 CLAUDE.md）。
显式设成 ollama 才会本机跑起来 Ollama（默认端口 11434，见 local_llm_client.py
顶部说明）——只用于没有 AWS 凭证时的本地调试，不是常规路径。

===========================================================================
给后端联调用的 mock 模式（不需要 Ollama、不需要 AWS）
===========================================================================
后端同学本机大概率没装 Ollama（要拉几 GB 模型、CPU 跑一次几十秒），
只是想把 HTTP 那一层调通的话，用 mock 模式起服务就行：

    # Windows PowerShell
    $env:EXTRACT_PROVIDER="mock"; $env:FILTER_PROVIDER="mock"; $env:RECOMMEND_PROVIDER="mock"
    uvicorn main:app --reload --app-dir ML/app --port 8001

    # bash
    EXTRACT_PROVIDER=mock FILTER_PROVIDER=mock RECOMMEND_PROVIDER=mock \
        uvicorn main:app --reload --app-dir ML/app --port 8001

mock 模式下：
- 响应的 JSON 结构跟真实模式**完全一样**，字段一个不少，可以照着写解析代码
- 秒回，不用等模型推理
- /recommend 返回的地点、坐标、距离都还是**真的**（来自 107 条真实数据集，
  纯 TF-IDF 计算不需要模型），只有推荐理由 reason 换成了 [MOCK] 开头的模板句
- /extract-travel-info 返回的**地点**是固定的两条假数据，不看输入内容；
  但 duration_days 会真的看输入文本（"3 days"/"3天"/"Day 1..Day 3" -> 对应天数，
  没写 -> null / needs_duration_input=true），这样"没说天数就弹窗问用户"那条
  分支在 mock 下也测得到，见 mock_client.mock_duration_days

等 HTTP 那层调通了，把这三个环境变量去掉就切回真实模型。
"""
import os
import sys
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402

from extraction import (  # noqa: E402
    MAX_ATTEMPTS,
    ExtractionFailedError,
    extract_with_retry,
    parse_and_validate,
)
from local_llm_client import local_extract  # noqa: E402
from orchestrator import (  # noqa: E402
    run_daily_itinerary,
    run_extraction,
    run_recommendation,
)

app = FastAPI(title="LoomyTrip Extract Service")#整个服务的"前台"本身


@app.get("/health")
def health() -> dict:
    """ALB target group 探活用（terraform/alb.tf 里 ml 目标组 health_check.path = "/health"）。
    之前只有占位版 app.py 有这个路由，main.py 一直没有——ECS 换上真实镜像后探活会
    一直 404，服务永远起不来（见 backend_alignment_brief / PR #22 讨论）。"""
    return {"status": "ok", "service": "loomytrip-ml"}


# 2026-08-11 起，bedrock 是固定默认值，不是临时测试窗口：Nova Lite 比本地 Ollama
# 快 ~10x（1.6s vs 几十秒）、任务2选型测试里唯一 0 漏抽/0 编造，实测调用量下
# 成本可忽略（远低于 $1）。仍可用 EXTRACT_PROVIDER=ollama / mock 显式切回本地调试。
EXTRACT_PROVIDER = os.environ.get("EXTRACT_PROVIDER", "bedrock")
if EXTRACT_PROVIDER == "bedrock":
    from bedrock_client import bedrock_extract  # noqa: E402

    EXTRACT_FN = bedrock_extract
elif EXTRACT_PROVIDER == "mock":
    from mock_client import mock_extract  # noqa: E402

    EXTRACT_FN = mock_extract
else:
    EXTRACT_FN = local_extract


class ExtractRequest(BaseModel):
    #规定 /extract 接口收到的请求长什么样（要有text字段）
    text: str
    source_name: str = "api_input"


def call_with_retry(text: str, source_name: str) -> TripExtraction:
#	调用抽取函数，失败了自动重试几次
    """
    调用 EXTRACT_FN 拿结果，闯 parse_and_validate 那两道关，失败就重试，
    最多试 MAX_ATTEMPTS 次；每次都失败的话转换成 502 错误返回给调用方。
    实际的解析/重试逻辑在 extraction.py 里，orchestrator.py 也复用同一套。
    """
    try:
        return extract_with_retry(text, source_name, EXTRACT_FN, MAX_ATTEMPTS)
    except ExtractionFailedError as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/extract", response_model=TripExtraction)
#	/extract 接口对应的处理函数
def extract(request: ExtractRequest) -> TripExtraction:
    return call_with_retry(request.text, request.source_name)


class ExtractTravelInfoRequest(BaseModel):
    """字段名对齐 AiPlanningClient.extractTravelInfo(rawContent, sourceUrl)。"""
#规定 /extract-travel-info 接口收到的请求长什么样
    raw_content: str
    source_url: str | None = None


class ChatMessageIn(BaseModel):
    """字段名对齐 chat_message 表：role（user/assistant/system）+ content。"""
#规定 /refine 接口收到的聊天记录长什么样
    role: str
    content: str


class RefineRequest(BaseModel):
    messages: list[ChatMessageIn]
    preference_text: str | None = None


def _extraction_response(result) -> dict:
    """
    把抽取结果整理成统一格式返回。

    注意这里**不含 recommended_places**：推荐是用户确认之后调 /recommend 才做的
    独立一步（见 ML/docs/ai_contract.md 第 1 节的流程图）。以前这两步绑在一起，
    等于用户还没确认就先推荐了，现在拆开了。
    """
    if result.error:
        if result.error_code == "NO_USEFUL_CONTENT":
            # 用户输入没有可用的旅行信息（全是废话/无关内容），不是服务出故障——
            # 422 而不是 502，跟 /plan-itinerary 的 NUM_DAYS_REQUIRED 一个道理：
            # 参数/输入的问题是 4xx，免得后端把"用户没说有用的东西"当成"AI 服务挂了"
            # 去排查。前缀码稳定，后端可以直接按前缀分支，不用解析整句话。
            raise HTTPException(status_code=422, detail=f"NO_USEFUL_CONTENT: {result.error}")
        raise HTTPException(status_code=502, detail=result.error)
    duration_days = result.extraction.duration_days
    return {
        "status": "OK",
        "destination": result.extraction.destination,
        "dates": result.extraction.dates,
        # 这一行是 /plan-itinerary 的 num_days 的**唯一来源**：只有抽取这一步看得到
        # 原始文本，后端拿到的是结构化 JSON，这里不给它就永远造不出"玩几天"。
        # 文本没说时是 null（见 ai_contract.md 第 5.5 节）。
        "duration_days": duration_days,
        # 恒等于 `duration_days is None`，但仍然单独给一个字段，理由是把"这时候
        # 该怎么办"这条规则**定死在 AI 侧**，而不是让三端各自解释一个 null：
        #   true  -> 天数没抽到，**必须先问用户**（前端弹窗），拿到答案再排行程
        #   false -> 天数已经有了，直接拿 duration_days 当 num_days 排
        # 不这么写的话，最省事的实现就是从 dates 反推天数，而 dates 反推是错的
        # （["2026-08-09","2026-08-11"] 分不清"玩3天"还是"这两天各去一次"），
        # 或者默默按 1 天排 —— 用户说了玩 3 天却拿到 1 天，还不会报错。
        "needs_duration_input": duration_days is None,
        "places": [p.model_dump() for p in result.extraction.places],
    }


@app.post("/extract-travel-info")
#    /extract-travel-info 接口对应的处理函数
def extract_travel_info(request: ExtractTravelInfoRequest) -> dict:
    """
    第一步·单次粗略路线场景（对应 planning_session.initial_brief）：
    不经过 chat_filter 降噪，直接抽取。**不做推荐。**
    """
    result = run_extraction(
        raw_content=request.raw_content,
        source_name=request.source_url or "api_input",
    )
    return _extraction_response(result)


@app.post("/refine")
#    /refine 接口对应的处理函数
def refine(request: RefineRequest) -> dict:
    """
    第一步·多轮聊天完善场景（对应 PlanningController.refine，chat_message 表的
    全部历史）：先过 chat_filter 降噪，再抽取。**同样不做推荐。**
    """
    result = run_extraction(
        messages=[m.model_dump() for m in request.messages],
        source_name="chat",
    )
    return _extraction_response(result)


class RecommendPlaceIn(BaseModel):
    """
    /recommend 请求里的单个地点。字段跟抽取结果的 places 对齐，
    额外允许 lat/lng——抽取阶段 coords 是 null，后端地理编码补上之后传进来。
    """
    name: str
    type: str = "other"
    lat: float | None = None
    lng: float | None = None
    activities: list[str] = []


class RecommendRequest(BaseModel):
    places: list[RecommendPlaceIn]
    destination: str = "Singapore"
    date: str | None = None
    preference_text: str | None = None
    top_n: int = 3
    mode: str = "hybrid"
    max_distance_km: float | None = None


@app.post("/recommend")
def recommend(request: RecommendRequest) -> dict:
    """
    第二步：用户**确认地点之后**才调。返回两块内容：

    - `ordered_stops`：把用户**自己确认的**地点重新排顺序 —— 传了 `date` 就查天气，
      把户外地点安排在不下雨的时段、室内地点安排在下雨的时段；同时段内按距离
      串成一条不折返的线路。没传 `date` 或天气查不到就只按距离排，不会报错。
    - `suggested_additions`（F-18）：推荐用户**没提过的**新地点。候选来自真实
      数据集（107 个新加坡景点，带真实经纬度），LLM 只负责从候选里挑选并写理由，
      所以不会推荐出不存在的地方。

    mode 对应 F-18 的 "nearby or similar"：
      similar = 只看文字相似度 / nearby = 只看距离 / hybrid = 两者结合（默认）
    传进来的 places 带 lat/lng 才能算距离，没有就自动退回纯相似度。
    """
    if not request.places:
        raise HTTPException(status_code=400, detail="places must not be empty")

    try:
        result = run_recommendation(
            places=[p.model_dump() for p in request.places],
            destination=request.destination,
            preference_text=request.preference_text,
            top_n=request.top_n,
            mode=request.mode,
            max_distance_km=request.max_distance_km,
            target_date=request.date,
        )
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"recommendation failed: {e}")

    return {"status": "OK", **result}


class PlanItineraryRequest(BaseModel):
    """
    字段名对齐后端 `AiPlanningClient.planItinerary()`。
    places 复用 /recommend 的形状，后端不用为这个接口另写一套 DTO。
    """
    places: list[RecommendPlaceIn]
    start_date: str | None = None
    # 2026-08-14：默认值从 1 改成 None（= 必填）。
    # 原来是 `default=1`，调用方漏传时会**静默**排成 1 天的行程并返回 200 ——
    # 用户明明说了"玩3天"，结果拿到 1 天，全链路没有任何地方会报错，只能靠肉眼
    # 发现。天数是这个接口唯一说不清就没法算的参数，宁可吵（400）也不要静默出错。
    # 后端 AiPlanningClientHttp.planItinerary() 一直是显式传值的，所以这个改动
    # 在现有调用方上不产生任何行为变化，只挡住"忘了接"这种情况。
    num_days: int | None = Field(default=None, ge=1, le=30)


@app.post("/plan-itinerary")
def plan_itinerary(request: PlanItineraryRequest) -> dict:
    """
    F-09 按天生成行程：把用户确认的地点分到第 1/2/3 天，每天内部再排好顺序。

    跟 /recommend 的分工（很容易混，这里说死）：
      /recommend      -> **一天**之内怎么排 + 推荐用户没提过的新地点
      /plan-itinerary -> **多天**怎么分，第 1 天去哪片区、第 2 天去哪片区。
                         **不做推荐**，要推荐就另外调 /recommend。

    怎么分天：先把所有地点按最近邻串成一条线路，再按天平均切段 —— 所以同一片
    区域的地点会落在同一天，不会来回横跨全岛。每一天内部照样走
    "下雨排室内、同时段按距离串线路"那套单天逻辑。

    `start_date` 选填，理由跟 /recommend 的 `date` 一样（用户说话里经常没日期）：
    - 传了 -> 每天各查各天的天气，按天气+距离排，返回每天的 `weather_summary`
    - 没传 -> 跳过天气，只按距离排，`weather_summary` / `time_of_day` 都是 `null`

    `num_days` **必填**，来源只有两个（见 ai_contract.md 第 5.5 节）：
    - `/extract-travel-info` 的 `duration_days` 不为 null -> 直接用它
    - 为 null（`needs_duration_input: true`）-> 前端弹窗问用户，把答案传进来
    漏传直接 400，不会默默按 1 天排。

    返回 `days` 数组，长度**永远等于 num_days**（某天没地点就是 `stops: []`），
    前端可以直接按天渲染，不用自己补空缺的天。每天的 `order` 从 1 重新开始。
    """
    if not request.places:
        raise HTTPException(status_code=400, detail="places must not be empty")

    if request.num_days is None:
        # 错误消息写英文并带一个稳定的前缀码：这条会经 HTTP 400 传到后端/前端，
        # 是跨服务的对外文本；前缀码让对方能靠字符串前缀分支，不用解析整句话。
        raise HTTPException(
            status_code=400,
            detail=(
                "NUM_DAYS_REQUIRED: num_days is required and has no default. "
                "Take it from /extract-travel-info's duration_days; when that is "
                "null (needs_duration_input=true), ask the user how many days the "
                "trip lasts and pass the answer here. Never infer it from dates."
            ),
        )

    try:
        result = run_daily_itinerary(
            places=[p.model_dump() for p in request.places],
            start_date=request.start_date,
            num_days=request.num_days,
        )
    except ValueError as e:
        # 参数传错了（日期格式、天数越界）是调用方的问题，用 400 而不是 502，
        # 免得后端把"我传错了"误当成"AI 服务挂了"去排查。
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"itinerary planning failed: {e}")

    return {"status": "OK", **result}
