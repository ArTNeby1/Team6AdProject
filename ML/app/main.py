"""
/extract 接口骨架。FastAPI骨架，Pydantic 校验，返回 JSON。

链路：文本进来 -> 调用模型客户端(现在指向 mock_extract) -> 解析+校验(带重试) -> JSON 返回。
8/2先把这条链路完整跑通。等真实 Bedrock 调用可用了，只需要把下面的 EXTRACT_FN
换成真实模型的函数（输入输出形状保持一致：text, source_name -> str），
路由逻辑本身不用改。

本地启动方式（在项目根目录 Team6AdProject 下执行）：
    uvicorn main:app --reload --app-dir ML/app --port 8001
启动后访问 http://127.0.0.1:8001/docs 能看到自动生成的交互式测试页面。
"""
import json
import sys
from pathlib import Path

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ValidationError

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402

from mock_client import mock_extract  # mock_client.py 跟本文件同目录，直接能 import

app = FastAPI(title="LoomyTrip Extract Service")

# 现在指向 mock，以后接通真实模型后只改这一行，下面的函数都不用动
EXTRACT_FN = mock_extract

# 模型最多允许连续答错几次，超过这个次数就放弃、报错给调用方
MAX_ATTEMPTS = 3


class ExtractRequest(BaseModel):
    text: str
    source_name: str = "api_input"


def parse_and_validate(raw_text: str) -> TripExtraction:
    """
    把模型返回的原始文字，转成校验通过的 TripExtraction 对象。
    要闯两道关：
    1. 格式关：raw_text 本身必须是合法 JSON，不然 json.loads 会抛 JSONDecodeError
    2. 内容关：就算是合法 JSON，字段也不一定齐全/类型不一定对，
       不然 model_validate 会抛 ValidationError（比如缺 name、坐标类型错）
    这两种异常都不在这里处理，直接抛出去，交给调用方(call_with_retry)决定要不要重试。
    """
    data = json.loads(raw_text)
    return TripExtraction.model_validate(data)


def call_with_retry(text: str, source_name: str) -> TripExtraction:
    """
    调用 EXTRACT_FN 拿结果，闯 parse_and_validate 那两道关；
    闯关失败就重试，最多试 MAX_ATTEMPTS 次；每次都失败的话，
    不让程序崩溃，而是转换成一个 502 错误返回给调用方，并说明原因。
    """
    last_error = None
    for attempt in range(1, MAX_ATTEMPTS + 1):
        raw_text = EXTRACT_FN(text, source_name=source_name)
        try:
            return parse_and_validate(raw_text)
        except (json.JSONDecodeError, ValidationError) as e:
            last_error = e
            print(f"[重试 {attempt}/{MAX_ATTEMPTS}] 模型输出不合规: {e}")

    raise HTTPException(
        status_code=502,
        detail=f"模型连续 {MAX_ATTEMPTS} 次都没能返回合规结果，最后一次错误：{last_error}",
    )


@app.post("/extract", response_model=TripExtraction)
def extract(request: ExtractRequest) -> TripExtraction:
    return call_with_retry(request.text, request.source_name)
