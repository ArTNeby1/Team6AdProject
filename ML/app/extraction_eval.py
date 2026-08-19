"""
LLM-as-judge 内容级评估：给定原文 + 模型抽出的地点，算 4 个指标。

背景：Pydantic 只能保证输出是「合法 JSON」，保证不了「内容准确」。要算
Precision / Recall / F1 就必须有一份「黄金答案」——原文里真实提到的所有地点。
demo 脚本（ML/eval/evaluate_extraction.py）用的是人工标注的固定 gold，但线上
真实的用户导入没有人工标注。

这里用 **LLM 当裁判**：让模型通读原文、列出所有真实地点，作为 gold；再用
**确定性的字符串匹配**算指标（匹配逻辑与 evaluate_extraction.py 完全一致，
可解释、可复现，不把打分本身也交给模型）。

  - Precision 精确率  ：抽出的地点里有几个对（对 = 命中 gold）→ 抓「乱编」
  - Recall    召回率  ：gold 里的真实地点抽到了几个            → 抓「漏抽」
  - F1                ：两者的综合分
  - Groundedness 忠实 ：抽出的地点原文里真的出现过吗（不需要 gold）→ 抓「幻觉」
"""
import json
import re

# 让裁判模型只做「通读原文、列出真实地点」这一件事，专门为评估建 gold 用。
JUDGE_SYSTEM_PROMPT = """You are a meticulous annotator building a gold-standard \
reference for evaluating a travel place-extraction model.

Read the travel text and list EVERY real, specific place a traveller could visit \
that is mentioned in it: attractions, landmarks, restaurants, markets, parks, \
neighbourhoods, malls. Include a place even if it is only mentioned in passing.

Rules:
- Only include places that literally appear in the text. Never invent or infer \
places that are not written there.
- Exclude generic words that are not a specific named place (e.g. "the beach", \
"dinner", "the hotel", "downtown") unless the text gives them a proper name.
- Output ONLY a JSON object of the form {"places": ["Name 1", "Name 2"]}. \
No markdown, no explanation."""


def normalize(name: str) -> str:
    """归一化：小写、去标点、压空格。让 'Marina Bay Sands ' 和 'marina bay sands' 算同一个。"""
    return re.sub(r"[^a-z0-9 ]", "", (name or "").lower()).strip()


def matches(a: str, b: str) -> bool:
    """模糊匹配：归一化后一方是另一方的子串就算命中（处理长短不一的地名）。"""
    na, nb = normalize(a), normalize(b)
    if not na or not nb:
        return False
    return na == nb or na in nb or nb in na


def score(source_text: str, predicted: list, gold: list) -> dict:
    """纯函数：给定原文、模型抽出的地点、gold 地点，算一张记分卡。不碰任何模型/IO。"""
    predicted = [p for p in (predicted or []) if normalize(p)]
    gold = [g for g in (gold or []) if normalize(g)]

    matched = [g for g in gold if any(matches(g, p) for p in predicted)]      # gold 里被抽到的
    missed = [g for g in gold if g not in matched]                            # 漏抽
    spurious = [p for p in predicted if not any(matches(g, p) for g in gold)]  # 抽错/编造

    precision = (len(predicted) - len(spurious)) / len(predicted) if predicted else 0.0
    recall = len(matched) / len(gold) if gold else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0

    # Groundedness 不需要 gold：抽出的地点在原文里是否出现过（子串级），抓幻觉。
    src_norm = normalize(source_text)
    grounded = [p for p in predicted if normalize(p) in src_norm]
    groundedness = len(grounded) / len(predicted) if predicted else 0.0

    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "groundedness": round(groundedness, 4),
        "gold_places": gold,
        "predicted_places": predicted,
        "matched": matched,
        "missed": missed,
        "spurious": spurious,
    }


def heuristic_gold(source_text: str) -> list:
    """无 LLM 裁判时的兜底 gold：抽原文里连续大写开头的多词短语（粗略地名候选）。
    仅用于 mock / 离线 / 裁判解析失败，质量不如 LLM 裁判。"""
    candidates = re.findall(
        r"\b([A-Z][a-zA-Z0-9]+(?: [A-Z][a-zA-Z0-9]+){1,4})\b", source_text or ""
    )
    seen, out = set(), []
    for candidate in candidates:
        key = normalize(candidate)
        if key and key not in seen:
            seen.add(key)
            out.append(candidate)
    return out


def parse_gold(raw_text: str) -> list:
    """解析裁判模型返回的 {"places": [...]}，尽量鲁棒：模型偶尔在 JSON 外面
    多带一句话，就从文本里捞第一个 JSON 对象再解析。解析不出来返回 []。"""
    data = None
    try:
        data = json.loads(raw_text)
    except (ValueError, TypeError):
        match = re.search(r"\{.*\}", raw_text or "", re.DOTALL)
        if match:
            try:
                data = json.loads(match.group(0))
            except ValueError:
                data = None
    if not isinstance(data, dict):
        return []
    places = data.get("places", [])
    if not isinstance(places, list):
        return []
    out = []
    for place in places:
        name = place.get("name", "") if isinstance(place, dict) else place
        name = str(name).strip()
        if name:
            out.append(name)
    return out
