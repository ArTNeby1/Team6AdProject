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

要用 /extract-travel-info、/refine，本机需要先跑起来 Ollama（默认端口 11434），
见 local_llm_client.py 顶部的说明。
"""
import os
import sys
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

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
from orchestrator import run_pipeline  # noqa: E402

app = FastAPI(title="LoomyTrip Extract Service")#整个服务的"前台"本身

EXTRACT_PROVIDER = os.environ.get("EXTRACT_PROVIDER", "ollama")
if EXTRACT_PROVIDER == "bedrock":
    from bedrock_client import bedrock_extract  # noqa: E402

    EXTRACT_FN = bedrock_extract
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


def _pipeline_response(result) -> dict:
    #把 pipeline 跑完的结果整理成统一格式返回
    if result.error:
        raise HTTPException(status_code=502, detail=result.error)
    return {
        "status": "OK",
        "destination": result.extraction.destination,
        "dates": result.extraction.dates,
        "places": [p.model_dump() for p in result.extraction.places],
        "recommended_places": [p.model_dump() for p in result.recommendation.recommended],
    }


@app.post("/extract-travel-info")
#    /extract-travel-info 接口对应的处理函数
def extract_travel_info(request: ExtractTravelInfoRequest) -> dict:
    """
    单次粗略路线场景（对应 planning_session.initial_brief）：不经过 chat_filter
    降噪，直接抽取 + 推荐。
    """
    result = run_pipeline(
        raw_content=request.raw_content,
        source_name=request.source_url or "api_input",
    )
    return _pipeline_response(result)


@app.post("/refine")
#    /refine 接口对应的处理函数
def refine(request: RefineRequest) -> dict:
    """
    多轮聊天完善场景（对应 PlanningController.refine，chat_message 表的全部历史）：
    先过 chat_filter 降噪，再抽取 + 推荐。
    """
    result = run_pipeline(
        messages=[m.model_dump() for m in request.messages],
        source_name="chat",
        preference_text=request.preference_text,
    )
    return _pipeline_response(result)
