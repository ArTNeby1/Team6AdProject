"""
本地部署的模型客户端，走 Ollama（本机跑 `ollama serve`，默认监听
127.0.0.1:11434），不再依赖云端 Bedrock/AWS 凭证。

跟 bedrock_client.py 的角色一样：只负责"打电话拿到模型的原始回答"，
不做 json.loads/schema 校验——那两道关 main.py 的 parse_and_validate()
已经做了。

用之前必须做的事：
1. 本机装好 Ollama（https://ollama.com），跑一次 `ollama serve`（大多数情况下
   装好之后是常驻服务，不用手动启动）。
2. 拉至少一个模型：`ollama pull llama3.1:8b-instruct-q4_K_M`
   （本机已经有 llama3.1:8b-instruct-q4_K_M 和 llama3:latest 两个，可以直接用
   来做"多模型分工"：比如 chat_filter 用小/快的模型，extract 用另一个）。
3. pip install -r requirements.txt（里面加了 requests）。

多模型分工（对应任务 4）：
call_local_model() 是所有 agent 共用的底层调用函数，model_id 每次调用都能单独
指定。chat_filter.py / local_llm_client.py(抽取) / recommend_agent.py 三个
阶段各自用哪个模型，由 orchestrator.py 里的环境变量分别控制，不用改这里的代码。
"""
import json
import os
from pathlib import Path

import requests

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
SCHEMA_PATH = SCHEMA_DIR / "trip_schema.json"

OLLAMA_HOST = os.environ.get("OLLAMA_HOST", "http://127.0.0.1:11434")

# 抽取阶段默认用哪个模型，可以用环境变量覆盖，方便换模型做对比测试
DEFAULT_EXTRACT_MODEL = os.environ.get("EXTRACT_MODEL", "llama3.1:8b-instruct-q4_K_M")

DEFAULT_NUM_GPU = int(os.environ.get("OLLAMA_NUM_GPU", "0"))

# enum 那条规则是实测加的：schema 里虽然写了 enum，但整份 JSON Schema 很长，
# 8B 小模型经常读不到那一层，把"国家博物馆"的 type 直接写成 "museum"，
# 连重试三次都是同一个错。把约束提到 system prompt 顶层、并给出常见词的映射
# （museum/park/temple -> attraction），模型才稳定听话。
SYSTEM_PROMPT = """You are a data extraction engine for travel content.
Read the text and output ONLY a single JSON object that matches this JSON
Schema exactly. Do not include markdown code fences, explanations, or any text
other than the JSON object itself.

CRITICAL RULES:
1. `type` MUST be EXACTLY one of these five values:
   "attraction", "restaurant", "hotel", "market", "other"
   Never output any other value. Map anything else to the closest one:
   museum / park / garden / temple / zoo / landmark / beach / mall -> "attraction"
   cafe / hawker centre / food court / bar                         -> "restaurant"
   hostel / resort / airbnb                                        -> "hotel"
2. `name` must be the place name only (under 255 characters), never a whole sentence.
3. The input may be in Chinese or English. Keep place names in their original language.
4. Never invent coordinates. Always output "coords": null.

JSON Schema:
{schema}
"""


def _load_schema() -> dict:
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def call_local_model(system_prompt: str, user_text: str, model_id: str, timeout: int = 300) -> str:
    """
    所有本地 agent 共用的底层调用函数：打给 Ollama，拿回纯文字回答。
    format="json" 让 Ollama 尽量只吐 JSON（不是 100% 保证，main.py 的
    parse_and_validate() 仍然会做真正的格式校验）。

    timeout 默认给到 300 秒：这台开发机被迫用 CPU 推理（见上面 DEFAULT_NUM_GPU
    的说明），8B 模型跑一次生成内容多一点的请求（比如 recommend_agent 那种
    要生成好几个地点+理由的）经常超过 Ollama 默认没设超时的 120 秒，
    换一台有能跑 GPU 的机器可以把这个调小。
    """
    response = requests.post(
        f"{OLLAMA_HOST}/api/chat",
        json={
            "model": model_id,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_text},
            ],
            "format": "json",
            "stream": False,
            "options": {"temperature": 0, "num_gpu": DEFAULT_NUM_GPU},
        },
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()["message"]["content"]


def local_extract(text: str, source_name: str = "api_input", model_id: str | None = None) -> str:
    """
    跟 mock_client.mock_extract / bedrock_client.bedrock_extract 同一个函数签名，
    main.py 的 EXTRACT_FN 换成这个函数就能直接用本地模型。

    source_name 没在函数体里用到，原因跟 bedrock_extract 一样：溯源信息现在
    只在请求级别记，签名保留只是为了三个客户端接口形状一致、能互换。
    """
    schema = _load_schema()
    system_prompt = SYSTEM_PROMPT.format(schema=json.dumps(schema, ensure_ascii=False))
    return call_local_model(system_prompt, text, model_id or DEFAULT_EXTRACT_MODEL)


if __name__ == "__main__":
    # 手动冒烟测试，需要本机 Ollama 已经在跑、且已经 pull 了 DEFAULT_EXTRACT_MODEL
    # 跑法（项目根目录下）：python ML/app/local_llm_client.py
    sample_path = Path(__file__).resolve().parent.parent / "samples" / "sample_1.txt"
    result = local_extract(sample_path.read_text(encoding="utf-8"), source_name="sample_1.txt")
    print(result)
