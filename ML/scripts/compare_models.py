"""
任务 2 Spike 脚本：用同样的 3 篇攻略文本调用 3 个候选 Bedrock 模型,
对比"是否合规的 JSON / 耗时 / token 用量(估算费用)"。

使用前必须做的事：
1. 把下面 MODELS 字典里的三个 model_id 换成你在 Bedrock 控制台
   Model catalog 里看到的准确 Model ID（每个模型详情页里能找到/复制）。
2. 确认这三个模型已经在 Model access 里显示 "Access granted"。
3. 确认本机 ~/.aws/credentials 里已经配置好可用的 access key。
"""

# ========== 第一部分：借工具 ==========
# boto3 = AWS 官方的 Python 工具包,专门用来跟 AWS 各种服务(包括 Bedrock)对话
# jsonschema = 用来检查"一段 JSON 格式对不对"的工具
# json/time/Path = Python 自带的工具,分别处理 JSON、计时、文件路径
import json
import time
from pathlib import Path

import boto3
from jsonschema import validate, ValidationError

REGION = "us-east-1"  # 调用哪个 AWS 地区的 Bedrock,要跟 Model ID/推理配置文件所在地区一致

# 告诉代码"东西都放在哪":样本文本在 samples 文件夹、
# schema 规则文件在 schema 文件夹、跑完的结果存到 results 文件夹
ML_DIR = Path(__file__).resolve().parent.parent
SAMPLES_DIR = ML_DIR / "samples"
SCHEMA_PATH = ML_DIR / "schema" / "trip_schema.json"
RESULTS_DIR = ML_DIR / "results"

# ========== 第二部分：这次要测哪几个模型 ==========
# 这是一个"名字对照表":左边是给人看的名字,右边是 AWS 系统认的真实 ID。
# 代码后面会遍历这个表,一个一个模型去测试。
# Claude 系列在这个账号里只能通过跨区域推理配置文件调用,所以带 "us." 前缀;
# Nova Lite 支持直接用普通 on-demand model id,不需要前缀。
MODELS = {
    "Claude Haiku 4.5": "us.anthropic.claude-haiku-4-5-20251001-v1:0",
    "Claude Sonnet 4.5": "us.anthropic.claude-sonnet-4-5-20250929-v1:0",
    "Amazon Nova Lite": "amazon.nova-lite-v1:0",
}

# 每个模型按"每 1000 个 token 多少钱"收费(token 可以简单理解成"一小块文字")。
# 这里存输入/输出的单价,后面拿来估算这次调用大概花了多少钱,不用去手动查账单。
# 大致单价,只是用来估算量级,不追求精确,需要的话可以对照 Bedrock 官网定价页面更新
PRICE_PER_1K_TOKENS = {
    "Claude Haiku 4.5": {"input": 0.001, "output": 0.005},
    "Claude Sonnet 4.5": {"input": 0.003, "output": 0.015},
    "Amazon Nova Lite": {"input": 0.00006, "output": 0.00024},
}

# ========== 第三部分：给 AI 的"角色设定" ==========
# 每次跟模型对话前,先偷偷告诉它"你现在的身份是什么、该怎么回答"。
# 这里让它当"专门从旅游博客抽数据的机器",并且必须严格按 {schema} 这份规则输出 JSON,
# 不能输出别的废话。{schema} 是占位符,运行时会被换成 trip_schema.json 的具体内容。
SYSTEM_PROMPT = """You are a data extraction engine for travel blog posts.
Read the travel blog text and output ONLY a single JSON object that matches
this JSON Schema exactly. Do not include markdown code fences, explanations,
or any text other than the JSON object itself.

JSON Schema:
{schema}
"""


def load_schema():
    """读取 trip_schema.json,返回一份 Python 能理解的规则字典"""
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def load_samples():
    """找到 samples 文件夹下所有 sample_*.txt 攻略文本,按文件名排序返回"""
    return sorted(SAMPLES_DIR.glob("sample_*.txt"))


def call_model(client, model_id, schema, text):
    """
    真正"打电话"给 AI 模型的地方。
    client         -- 已经连好的 Bedrock 连线
    model_id       -- 打给哪个模型(表里那个真实 ID)
    schema         -- 抽取规则,会被塞进角色设定里一起发给模型
    text           -- 这次要模型阅读的那篇攻略原文
    返回：模型回复的原始文字、这次调用花了几秒、用了多少 token
    """
    # 把规则塞进角色设定文字里,变成这次真正要发送的 system prompt
    system_prompt = SYSTEM_PROMPT.format(schema=json.dumps(schema, ensure_ascii=False))

    start = time.time()  # 打电话前记一次时间
    response = client.converse(
        modelId=model_id,                                  # 找哪个模型接电话
        system=[{"text": system_prompt}],                   # 先说一遍角色设定
        messages=[{"role": "user", "content": [{"text": text}]}],  # 再把攻略文本当"用户的话"发过去
        inferenceConfig={
            "maxTokens": 2000,  # 限制它最多能说多长,防止啰嗦、也省钱
            "temperature": 0,   # 让它尽量"保守确定"地回答,不要自由发挥/瞎编,抽取任务要的是准确
        },
    )
    elapsed = time.time() - start  # 打完电话后再记一次时间,相减就是这次耗时

    raw_text = response["output"]["message"]["content"][0]["text"]  # 模型说的原话
    usage = response["usage"]  # inputTokens, outputTokens -- 这次对话用了多少 token
    return raw_text, elapsed, usage


def strip_code_fence(raw_text):
    """
    有些模型喜欢在 JSON 外面套一层 ```json ... ``` 的代码块包装(像加了个装饰边框)。
    这个函数就是把这层包装纸撕掉,只留纯 JSON 内容,不然后面解析会报错。
    """
    text = raw_text.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1] if "\n" in text else text
        if text.endswith("```"):
            text = text.rsplit("```", 1)[0]
    return text.strip()


def validate_output(raw_text, schema, source_name):
    """
    检查模型答得对不对。
    第一步：能不能被当成合法 JSON 读进来(格式关)。
    第二步：内容符不符合 trip_schema.json 里定义的规则(schema 关)。
    返回：解析出来的数据(失败则是 None)、状态说明字符串("PASS" 或 "FAIL: ...")
    """
    #查格式
    text = strip_code_fence(raw_text)
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        return None, f"JSON 解析失败: {e}"

    # source 字段按 schema 说明是由代码填入的,不是模型该生成的内容,
    # 补字段：校验前先补上,避免误判模型"漏了必填字段"
    for place in data.get("places", []):
        place.setdefault("source", source_name)

    try:
        validate(instance=data, schema=schema)#检查内容（是否符合 Schema 规则）
        return data, "PASS"
    except ValidationError as e:
        return data, f"FAIL: {e.message} (位置: {list(e.path)})"


def estimate_cost(model_name, usage):
    """拿这次调用实际用的 token 数,乘以单价表里的单价,算出这次大概花了多少钱"""
    price = PRICE_PER_1K_TOKENS.get(model_name)
    if not price:
        return None
    input_cost = usage["inputTokens"] / 1000 * price["input"]
    output_cost = usage["outputTokens"] / 1000 * price["output"]
    return input_cost + output_cost


def main():
    """
    把上面所有零件串起来跑一遍:
    外层循环换模型(3 个),内层循环换文本(3 篇),一共调用 3x3=9 次,
    每次都记录"听不听话/耗时/花费",最后打印一张三个模型的汇总对比表。
    """
    RESULTS_DIR.mkdir(exist_ok=True)
    schema = load_schema()
    samples = load_samples()
    client = boto3.client("bedrock-runtime", region_name=REGION)  # 建立连到 Bedrock 的总线路

    summary = []  # 存放每个模型最终的汇总数据,最后统一打印

    for model_name, model_id in MODELS.items():  # 外层循环：一个个模型来
        print(f"\n=== {model_name} ({model_id}) ===")
        pass_count = 0
        total_time = 0.0
        total_cost = 0.0

        for sample_path in samples:  # 内层循环：每个模型都要测完这几篇样本
            text = sample_path.read_text(encoding="utf-8")
            try:
                raw_text, elapsed, usage = call_model(client, model_id, schema, text)
            except Exception as e:
                print(f"[{sample_path.name}] 调用失败: {e}")
                continue

            data, status = validate_output(raw_text, schema, sample_path.name)
            cost = estimate_cost(model_name, usage) or 0.0
            total_time += elapsed
            total_cost += cost
            if status == "PASS":
                pass_count += 1

            print(f"[{sample_path.name}] {status} | 耗时 {elapsed:.2f}s | "
                  f"tokens in/out {usage['inputTokens']}/{usage['outputTokens']} | "
                  f"估算费用 ${cost:.5f}")

            # 把这次模型的原始回答存成单独文件,方便之后人工对照原文核对
            # 有没有漏抽地点/编造内容(这一步电脑没法自动判断,必须人工看)
            out_file = RESULTS_DIR / f"{model_name.replace(' ', '_')}_{sample_path.stem}.json"
            out_file.write_text(
                json.dumps(data, ensure_ascii=False, indent=2) if data else raw_text,
                encoding="utf-8",
            )

        n = len(samples)
        summary.append({
            "model": model_name,
            "pass_rate": f"{pass_count}/{n}",
            "avg_time": total_time / n if n else 0,
            "total_cost": total_cost,
        })

    # 三个模型都跑完之后,打印一张总结表:每个模型通过了几篇、平均耗时、总花费
    # 这几个数字就是要填进 model_selection.md 对比表里的内容
    print("\n\n=== 汇总 ===")
    for row in summary:
        print(f"{row['model']:20s} schema通过 {row['pass_rate']:5s} "
              f"平均耗时 {row['avg_time']:.2f}s  预估总费用 ${row['total_cost']:.5f}")


if __name__ == "__main__":
    main()
# 运行 python spike_script.py
#     ↓
# if __name__ == "__main__":  # 判断成立
#     ↓
# main()  # 开始执行
#     ↓
# main() 内部调用：
#     1. load_schema()       → 读规则
#     2. load_samples()      → 找样本
#     3. boto3.client()      → 连 Bedrock
#     4. for 循环 3 个模型：
#         5. call_model()    → 调 AI
#         6. validate_output() → 检查质量
#         7. estimate_cost()  → 算钱
#         8. 保存结果文件
#     9. 打印汇总表
