"""
LLM 抽取输出「准确性」评估 —— demo 版。

Pydantic 只能保证输出是「合法 JSON」(结构有效)，但保证不了「内容准确」：
模型可以吐出完美格式的 JSON，却把地点漏掉一半、或者凭空编造。
这个脚本就是补上那一层：拿模型输出和人工标注的「黄金答案」比，算一张记分卡。

指标：
  - Precision(精确率)：模型抽出的地点里，有几个是对的？→ 抓「乱编」
  - Recall(召回率)  ：原文真实的地点里，模型抽到了几个？→ 抓「漏抽」
  - F1              ：两者的综合分
  - Groundedness(忠实度)：每个抽出的地点，原文里真的出现过吗？→ 抓「幻觉」

跑法(在项目根目录 Team6AdProject 下)：
    python ML/eval/evaluate_extraction.py
"""
import json
import re
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent.parent / "app"
sys.path.insert(0, str(APP_DIR))
from mock_client import mock_extract  # noqa: E402  这就是被评估的「agent」

SAMPLE = Path(__file__).resolve().parent.parent / "samples" / "sample_1.txt"

# 人工标注的黄金答案：原文里真实提到的所有地点(一个人读一遍标出来的)
GOLD_PLACES = [
    "Gardens by the Bay",
    "Satay by the Bay",
    "Merlion Park",
    "Marina Bay Sands SkyPark Observation Deck",
    "Chinatown Street Market",
    "Chinatown Complex Food Centre",
    "Universal Studios Singapore",
    "Wings of Time",
    "Vivo City",
]


def normalize(name: str) -> str:
    """归一化：小写、去标点、压空格。让 'Marina Bay Sands ' 和 'marina bay sands' 算同一个。"""
    return re.sub(r"[^a-z0-9 ]", "", name.lower()).strip()


def matches(a: str, b: str) -> bool:
    """模糊匹配：归一化后一方是另一方的子串就算命中(处理长短不一的地名)。"""
    na, nb = normalize(a), normalize(b)
    return na == nb or na in nb or nb in na


def main() -> None:
    source_text = SAMPLE.read_text(encoding="utf-8")

    # 1) 跑被评估的 agent，拿到结构化输出
    raw = mock_extract(source_text, source_name="sample_1.txt")
    predicted = [p["name"] for p in json.loads(raw)["places"]]

    # 2) Precision / Recall / F1（按地点实体匹配黄金答案）
    tp = [g for g in GOLD_PLACES if any(matches(g, p) for p in predicted)]  # 命中
    missed = [g for g in GOLD_PLACES if g not in tp]                        # 漏抽
    spurious = [p for p in predicted if not any(matches(g, p) for g in GOLD_PLACES)]  # 抽错/编造

    precision = (len(predicted) - len(spurious)) / len(predicted) if predicted else 0.0
    recall = len(tp) / len(GOLD_PLACES) if GOLD_PLACES else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0

    # 3) Groundedness：每个抽出的地点，原文里真的出现过吗？
    src_norm = normalize(source_text)
    grounded = [p for p in predicted if normalize(p) in src_norm]
    groundedness = len(grounded) / len(predicted) if predicted else 0.0

    # ---- 打印记分卡 ----
    print("=" * 60)
    print("  LLM 抽取输出 · 准确性记分卡  (sample_1.txt)")
    print("=" * 60)
    print(f"  原文真实地点(人工标注)：{len(GOLD_PLACES)} 个")
    print(f"  模型抽出地点：          {len(predicted)} 个 -> {predicted}")
    print("-" * 60)
    print(f"  Precision 精确率 : {precision:5.0%}   (抽出的里有几个是对的)")
    print(f"  Recall    召回率 : {recall:5.0%}   (真实的里抽到了几个)")
    print(f"  F1        综合分 : {f1:5.0%}")
    print(f"  Groundedness忠实: {groundedness:5.0%}   (抽出的原文里都有 -> 没幻觉)")
    print("-" * 60)
    print(f"  漏抽的 {len(missed)} 个地点：{missed}")
    print(f"  编造的 {len(spurious)} 个地点：{spurious if spurious else '无'}")
    print("=" * 60)
    print("  结论：JSON 结构 100% 合法(Pydantic 全过)，但召回只有 "
          f"{recall:.0%} —")
    print("       模型悄悄漏掉了一半以上地点。这类错误 Pydantic 校验")
    print("       永远抓不到，只有内容级评估才能暴露。")
    print("=" * 60)


if __name__ == "__main__":
    main()
