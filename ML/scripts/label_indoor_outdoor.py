"""
给数据集里的 107 个地点各打一个「室内 / 室外」标记，结果写回
`ML/data/processed/singapore_attractions.csv` 的 `indoor_outdoor` 列。

为什么需要这一步：天气工具能查出"下午有雨"，但**光知道下雨没用** ——
agent 得知道哪些地点是室内的，才能把户外行程挪到不下雨的时段。
没有这一列，天气数据就只是个摆设。

分两步，先规则后模型：
1. 关键词规则：地点名和描述里出现 museum / temple / theatre 之类的词就判室内，
   park / garden / beach 之类判室外。这一步不需要模型，秒出结果，可重复。
2. 规则判不出来的，再交给 LLM 逐条分类（需要本机 Ollama）。
   加 --no-llm 就跳过这步，剩下的统一标成 unknown。

为什么不直接全用 LLM：107 条在 CPU 上逐条跑要十几分钟，而且模型对
"Sri Mariamman Temple" 这种一眼就能看出来的也要想半天。规则能确定的就别问模型，
省时间也更稳定（模型每次跑结果可能不一样，规则不会）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/scripts/label_indoor_outdoor.py           # 规则 + LLM 兜底
    python ML/scripts/label_indoor_outdoor.py --no-llm  # 只用规则，不需要 Ollama
    python ML/scripts/label_indoor_outdoor.py --dry-run # 只看统计，不写文件
"""
import argparse
import json
import sys
from pathlib import Path

import pandas as pd

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR / "app"))

CSV_PATH = ML_DIR / "data" / "processed" / "singapore_attractions.csv"

# 命中就判"室内"。注意顺序无关，但 INDOOR 优先于 OUTDOOR 判断（见 classify_by_rules）：
# "Chinatown Heritage Centre" 同时含 centre(室内) 和 chinatown，应该判室内。
INDOOR_KEYWORDS = (
    "museum", "gallery", "centre", "center", "theatre", "theater", "mall",
    "aquarium", "cinema", "mosque", "temple", "church", "cathedral",
    "synagogue", "shrine", "library", "hotel", "institute", "college",
    "building", "hall", "arcade", "observatory", "planetarium",
)

# 命中就判"室外"
# memorial / statue / obelisk / district 这几个是实测补的：漏了它们的时候，
# 战争纪念碑和"某某文化区"这类街区会掉进 LLM 那一步，而模型对这类判得很差
# （把 Kranji War Memorial、Bras Basah 文化区都判成了 indoor）。
# 规则比模型稳定也可解释，能用规则确定的就不要交给模型。
OUTDOOR_KEYWORDS = (
    "park", "garden", "beach", "island", "trail", "reservoir", "barrage",
    "square", "bay", "river", "walk", "bridge", "monument", "cemetery",
    "street", "quay", "jetty", "pier", "hill", "forest", "wetland",
    "boardwalk", "promenade", "zoo", "safari", "farm",
    "memorial", "statue", "obelisk", "cenotaph", "district", "neighbourhood",
    "nature reserve", "waterfront", "sentosa",
)

# 必须要求 JSON 输出：call_local_model 固定开了 Ollama 的 format="json"，
# 让模型"只回一个单词"它会被迫包成 JSON，实测直接返回空对象 { } 什么都拿不到。
LLM_SYSTEM_PROMPT = """You classify Singapore tourist attractions as indoor or outdoor.

- indoor  = visitors are mostly under a roof (museums, malls, aquariums)
- outdoor = visitors are mostly exposed to weather (parks, beaches, trails)
- mixed   = genuinely both (a theme park with indoor rides, a garden with domes)

Output ONLY a JSON object in exactly this form, nothing else:
{"label": "indoor"}

`label` must be exactly one of: "indoor", "outdoor", "mixed".
"""


def classify_by_rules(name: str, description: str) -> str | None:
    """
    规则分类。返回 "indoor" / "outdoor" / None（规则判不出来）。

    只看名字不看描述里的零散词：描述里常常提到周边环境（"near the park"），
    拿描述做关键词匹配误判率高。名字里的通名（Museum / Park）才是可靠信号。
    """
    text = (name or "").lower()

    hit_indoor = any(k in text for k in INDOOR_KEYWORDS)
    hit_outdoor = any(k in text for k in OUTDOOR_KEYWORDS)

    # 两边都命中就算判不出来，交给 LLM。例如 "Marina Bay Sands"（bay=室外通名，
    # 但实际是个室内综合体），这种硬套规则会错。
    if hit_indoor and hit_outdoor:
        return None
    if hit_indoor:
        return "indoor"
    if hit_outdoor:
        return "outdoor"
    return None


def classify_by_llm(name: str, description: str) -> str:
    """规则判不出来的交给模型。失败/输出不合法一律返回 unknown，不猜。"""
    from local_llm_client import call_local_model

    user_text = f"Name: {name}\nDescription: {(description or '')[:300]}"
    try:
        raw = call_local_model(LLM_SYSTEM_PROMPT, user_text, "llama3:latest").strip()
    except Exception as e:
        print(f"    [LLM failed] {name[:40]}: {e}")
        return "unknown"

    # 先按约定的 {"label": "..."} 解析；模型偶尔会换个 key 名或多包一层，
    # 所以解析失败时退回在原始文字里找关键词，两道都不中才认输标 unknown。
    try:
        value = str(json.loads(raw).get("label", "")).lower()
        if value in ("indoor", "outdoor", "mixed"):
            return value
    except (json.JSONDecodeError, AttributeError):
        pass

    lowered = raw.lower()
    for label in ("outdoor", "indoor", "mixed"):
        if label in lowered:
            return label
    return "unknown"


def main():
    parser = argparse.ArgumentParser(description="Label dataset places as indoor/outdoor")
    parser.add_argument("--no-llm", action="store_true", help="rules only, no Ollama needed")
    parser.add_argument("--dry-run", action="store_true", help="print stats, do not write the CSV")
    args = parser.parse_args()

    df = pd.read_csv(CSV_PATH)
    print(f"loaded {len(df)} rows from {CSV_PATH.name}")

    labels, sources = [], []
    undecided = []

    for _, row in df.iterrows():
        label = classify_by_rules(str(row["name"]), str(row.get("description", "")))
        if label:
            labels.append(label)
            sources.append("rule")
        else:
            labels.append(None)
            sources.append(None)
            undecided.append(row["name"])

    decided = sum(1 for x in labels if x)
    print(f"rules decided {decided}/{len(df)} ({decided / len(df) * 100:.0f}%), "
          f"{len(undecided)} left")

    if undecided and not args.no_llm:
        print(f"asking the LLM about the remaining {len(undecided)} (needs Ollama running)...")
        for i, (idx, row) in enumerate(df.iterrows()):
            if labels[i] is not None:
                continue
            labels[i] = classify_by_llm(str(row["name"]), str(row.get("description", "")))
            sources[i] = "llm"
            print(f"  [{i + 1}/{len(df)}] {str(row['name'])[:45]:47s} -> {labels[i]}")
    else:
        for i, v in enumerate(labels):
            if v is None:
                labels[i] = "unknown"
                sources[i] = "unresolved"

    df["indoor_outdoor"] = labels
    df["indoor_outdoor_source"] = sources

    print("\n=== distribution ===")
    print(df["indoor_outdoor"].value_counts().to_string())
    print("\n=== how it was decided ===")
    print(df["indoor_outdoor_source"].value_counts().to_string())

    if args.dry_run:
        print("\n[dry-run] CSV not written")
        return

    df.to_csv(CSV_PATH, index=False, encoding="utf-8")
    print(f"\n[OK] written back to {CSV_PATH}")


if __name__ == "__main__":
    main()
