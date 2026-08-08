"""
"解析+校验+重试"这套防御逻辑，从 main.py 抽出来放这里。

原因：orchestrator.py（多 agent 编排）和 main.py（FastAPI 路由）都要用同一套
重试逻辑，如果留在 main.py 里，orchestrator.py import main 会跟 main import
orchestrator 形成循环依赖。抽成独立模块，两边各自 import 这里就没有这个问题。
"""
import json
import sys
from pathlib import Path
from typing import Callable

from pydantic import ValidationError

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))  # trip_models.py 不在标准包路径下，手动加进搜索路径

from trip_models import TripExtraction  # noqa: E402

MAX_ATTEMPTS = 3


class ExtractionFailedError(Exception):
    """模型连续 MAX_ATTEMPTS 次都没能返回合规结果。"""


def parse_and_validate(raw_text: str) -> TripExtraction:
    """
    把模型返回的原始文字，转成校验通过的 TripExtraction 对象。
    要闯两道关：
    1. 格式关：raw_text 本身必须是合法 JSON，不然 json.loads 会抛 JSONDecodeError
    2. 内容关：就算是合法 JSON，字段也不一定齐全/类型不一定对，
       不然 model_validate 会抛 ValidationError
    这两种异常都不在这里处理，直接抛出去，交给调用方(extract_with_retry)决定要不要重试。
    """
    data = json.loads(raw_text)
    return TripExtraction.model_validate(data)


def extract_with_retry(
    text: str,
    source_name: str,
    extract_fn: Callable[..., str],
    max_attempts: int = MAX_ATTEMPTS,
) -> TripExtraction:
    """
    调用 extract_fn(text, source_name=...) 拿结果，闯 parse_and_validate 那两道关；
    闯关失败就重试，最多试 max_attempts 次；每次都失败就抛 ExtractionFailedError，
    由调用方（main.py 转成 502，orchestrator.py 转成 pipeline 的 error 字段）决定怎么处理。
    """
    last_error = None
    for attempt in range(1, max_attempts + 1):
        # 第 2 次起把上一次的校验错误附在输入后面。
        # 之前这里是原样再问一遍，模型没有任何新信息，自然还是给同一个错误答案——
        # 三次重试等于白跑三次。实测例子：llama3 把"国家博物馆"的 type 输出成
        # "museum"（enum 里没有这个值），不告诉它错在哪就会连错三次。
        prompt_text = text
        if last_error is not None:
            prompt_text = (
                f"{text}\n\n"
                f"[SCHEMA VIOLATION IN YOUR PREVIOUS OUTPUT — fix it and output the full JSON again]\n"
                f"{last_error}"
            )

        raw_text = extract_fn(prompt_text, source_name=source_name)
        try:
            return parse_and_validate(raw_text)
        except (json.JSONDecodeError, ValidationError) as e:
            last_error = e
            print(f"[retry {attempt}/{max_attempts}] model output failed validation: {e}")

    raise ExtractionFailedError(
        # 错误消息用英文：这条会通过 HTTP 502 的 detail 传给后端/前端，
        # 是跨服务的对外文本，不是只给自己看的日志。
        f"model failed to return a schema-valid result after {max_attempts} attempts; "
        f"last error: {last_error}"
    )
