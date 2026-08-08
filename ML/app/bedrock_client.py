"""
真实 Bedrock 模型客户端，函数签名对齐 mock_client.py：
输入 (text, source_name) -> 输出 str。main.py 要切真实调用时，
只需要把 EXTRACT_FN 从 mock_extract 换成这里的 bedrock_extract，
main.py 其它代码（parse_and_validate / call_with_retry）不用改。

用之前必须做的事：
1. MODEL_ID 现在占位成 Nova Lite（Bedrock 里通常最容易批下来），
   等实际批下来的是哪个模型，换成 compare_models.py MODELS 字典里对应的
   真实 model_id 即可。
2. 本机 ~/.aws/credentials 配置好有权限调用 bedrock-runtime 的 access key。
3. Bedrock 控制台 Model access 页面里，MODEL_ID 对应的模型要显示
   "Access granted"。
"""
import json
import sys
from pathlib import Path

import boto3

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
sys.path.insert(0, str(SCHEMA_DIR))

REGION = "us-east-1"
# 占位模型，权限批下来是哪个就换成哪个的真实 model_id
MODEL_ID = "amazon.nova-lite-v1:0"

SCHEMA_PATH = SCHEMA_DIR / "trip_schema.json"

SYSTEM_PROMPT = """You are a data extraction engine for travel blog posts.
Read the travel blog text and output ONLY a single JSON object that matches
this JSON Schema exactly. Do not include markdown code fences, explanations,
or any text other than the JSON object itself.

JSON Schema:
{schema}
"""

_client = None  # 懒加载：import 这个文件时不强制要求本机已经配好 AWS 凭证


def _get_client():
    global _client
    if _client is None:
        _client = boto3.client("bedrock-runtime", region_name=REGION)
    return _client


def _load_schema() -> dict:
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def _strip_code_fence(raw_text: str) -> str:
    """跟 compare_models.py 里同名函数逻辑一致：有些模型会在 JSON 外面套一层
    ```json ... ``` 代码块。main.py 的 parse_and_validate 不处理这层包装，
    所以必须在这里先撕掉，保证返回的是能直接 json.loads 的纯文本。
    """
    text = raw_text.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1] if "\n" in text else text
        if text.endswith("```"):
            text = text.rsplit("```", 1)[0]
    return text.strip()


def call_bedrock_model(
    system_prompt: str, user_text: str, model_id: str | None = None, max_tokens: int = 2000
) -> str:
    """
    跟 local_llm_client.call_local_model() 角色一样：所有 Bedrock agent 共用的
    底层调用函数，chat_filter.py / recommend_agent.py 想切到 Bedrock 时直接调
    这个，不用各自重复写 boto3 调用代码。model_id 不传就用抽取阶段选定的
    MODEL_ID（Nova Lite），传了就用调用方指定的（比如给推荐阶段单独配一个）。
    """
    response = _get_client().converse(
        modelId=model_id or MODEL_ID,
        system=[{"text": system_prompt}],
        messages=[{"role": "user", "content": [{"text": user_text}]}],
        inferenceConfig={
            "maxTokens": max_tokens,
            "temperature": 0,
        },
    )
    return _strip_code_fence(response["output"]["message"]["content"][0]["text"])


def bedrock_extract(text: str, source_name: str = "api_input") -> str:
    """
    跟 mock_client.mock_extract 同一个函数签名，main.py 的 EXTRACT_FN
    换成这个函数就能直接用。只负责"打电话拿到模型的原始回答"，
    不做完整的 json.loads/schema 校验——那两道关 main.py 的
    parse_and_validate() 已经做了，这里重复做是多余的。

    source_name 现在没在函数体里用到：schema 去掉 per-place 的 source 字段后，
    溯源信息只在请求级别记（main.py 的 ExtractRequest.source_name），但签名
    还是保留这个参数，跟 mock_extract / local_llm_client.local_extract 的接口
    形状对齐，main.py 里三个客户端可以互换，不用改调用代码。
    """
    schema = _load_schema()
    system_prompt = SYSTEM_PROMPT.format(schema=json.dumps(schema, ensure_ascii=False))
    return call_bedrock_model(system_prompt, text, MODEL_ID)


if __name__ == "__main__":
    # 手动冒烟测试，需要本机已配好 AWS 凭证 + MODEL_ID 对应模型权限已批准
    # 跑法（项目根目录下）：python ML/app/bedrock_client.py
    sample_path = Path(__file__).resolve().parent.parent / "samples" / "sample_1.txt"
    result = bedrock_extract(sample_path.read_text(encoding="utf-8"), source_name="sample_1.txt")
    print(result)
