"""
把 S0 model_selection.md 里"人工逐条核对"那道工序，改写成一个可重复运行的脚本。

背景（不是重新做一次选型，是把已经做过的评估**形式化**）：
`docs/model_selection.md` 第三、四部分的结论（Nova Lite 零漏抽零编造）是当时
人工拿 `results/*.json` 对照 `samples/ground_truth/*.json` 逐条读出来的，
过程本身没有留下代码——换一批新样本、或者别人想复核，得重新人工看一遍。
这个脚本把同一件事变成一个函数，任何时候跑一次就有结果，答辩现场也能演示。

**匹配严格度是这里最该讲清楚的一点**：地点名字很少逐字相同，比如
ground_truth 写"Chinatown Street Market (Pagoda Street)"，模型写
"Chinatown Street Market on Pagoda Street"——语义上是同一个地方，
人工核对时会算对，纯字符串相等会算错。所以这里算两种严格度：

  strict：归一化后（转小写、去标点空格）完全相等，才算命中
  fuzzy ：用 difflib 序列相似度 >= FUZZY_THRESHOLD，允许小的用词差异

**fuzzy 不是为了让分数好看而放水**——它是可解释、有阈值、能复现的算法
（difflib.SequenceMatcher，Python 标准库），不是"看着像就算"。
报告里两个数字都会给出，strict 是下限、fuzzy 是上限，人工核对的真实结果
大概率落在两者之间。这一点在 docs/model_evaluation.md 里会重点解释。

跑法（在 ML/ 目录下）：
    python scripts/evaluate_extraction.py
"""
import difflib
import glob
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")  # Windows 终端默认 GBK

RESULTS_DIR = Path(__file__).resolve().parent.parent / "results"
GROUND_TRUTH_DIR = Path(__file__).resolve().parent.parent / "samples" / "ground_truth"

FUZZY_THRESHOLD = 0.82  # 手动试出来的：能吃掉 "on" vs "(...)" 这种小差异，又不会把不同地点误判成同一个


def _normalize(name: str) -> str:
    """跟 recommend_agent._normalize_name 同一个思路：只保留字母数字，忽略大小写/标点/空格。"""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def _fuzzy_match(a: str, b: str) -> bool:
    return difflib.SequenceMatcher(None, _normalize(a), _normalize(b)).ratio() >= FUZZY_THRESHOLD


def _match_places(truth_places: list[dict], pred_places: list[dict]) -> dict:
    """
    单篇样本的匹配结果。用两种严格度分别贪心匹配（每个 truth 地点最多配一个 pred，
    避免同一个预测地点被重复计成命中多个真实地点，会人为拉高分数）。

    返回：{"strict": {...}, "fuzzy": {...}}，每个里面有
        matched / missed（漏抽的 truth 名字）/ fabricated（编造的 pred 名字）
    """
    result = {}
    for mode, matcher in (("strict", lambda a, b: _normalize(a) == _normalize(b)), ("fuzzy", _fuzzy_match)):
        remaining_pred = list(pred_places)
        matched, missed = [], []
        for t in truth_places:
            hit = next((p for p in remaining_pred if matcher(t["name"], p["name"])), None)
            if hit:
                matched.append((t["name"], hit["name"]))
                remaining_pred.remove(hit)
            else:
                missed.append(t["name"])
        fabricated = [p["name"] for p in remaining_pred]
        result[mode] = {"matched": matched, "missed": missed, "fabricated": fabricated}
    return result


def _prf1(n_matched: int, n_missed: int, n_fabricated: int) -> tuple[float, float, float]:
    """precision = 抽出来的里有多少是对的；recall = 该抽的里抽到了多少；F1 是两者调和平均。"""
    predicted = n_matched + n_fabricated
    truth = n_matched + n_missed
    precision = n_matched / predicted if predicted else 0.0
    recall = n_matched / truth if truth else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    return precision, recall, f1


def evaluate_model(model_name: str) -> dict:
    """跑一个模型在全部 3 篇样本上的指标。"""
    per_sample = []
    for gt_path in sorted(GROUND_TRUTH_DIR.glob("sample_*.json")):
        sample_id = gt_path.stem  # "sample_1"
        result_path = RESULTS_DIR / f"{model_name}_{sample_id}.json"
        if not result_path.exists():
            continue

        truth = json.loads(gt_path.read_text(encoding="utf-8"))
        pred = json.loads(result_path.read_text(encoding="utf-8"))

        match = _match_places(truth["places"], pred["places"])

        # 日期编造：这三份 ground truth 的 dates 全是空数组（原文没提年份），
        # 模型输出只要不是空，逻辑上就是编的——不用比对具体日期对不对，
        # 有就是编，因为压根没有任何依据能推出年份。
        date_hallucinated = bool(truth["dates"] == [] and pred["dates"])

        per_sample.append(
            {
                "sample": sample_id,
                "match": match,
                "date_hallucinated": date_hallucinated,
                "n_truth_places": len(truth["places"]),
                "n_pred_places": len(pred["places"]),
            }
        )

    agg = {}
    for mode in ("strict", "fuzzy"):
        m = sum(len(s["match"][mode]["matched"]) for s in per_sample)
        missed = sum(len(s["match"][mode]["missed"]) for s in per_sample)
        fab = sum(len(s["match"][mode]["fabricated"]) for s in per_sample)
        precision, recall, f1 = _prf1(m, missed, fab)
        agg[mode] = {
            "matched": m, "missed": missed, "fabricated": fab,
            "precision": round(precision, 3), "recall": round(recall, 3), "f1": round(f1, 3),
        }

    return {
        "model": model_name,
        "n_samples": len(per_sample),
        "per_sample": per_sample,
        "aggregate": agg,
        "date_hallucination_rate": round(
            sum(s["date_hallucinated"] for s in per_sample) / len(per_sample), 2
        ) if per_sample else None,
    }


def discover_models() -> list[str]:
    """从 results/ 目录的文件名反推有哪些模型跑过，不用手动维护模型名单列表。"""
    names = set()
    for f in glob.glob(str(RESULTS_DIR / "*_sample_*.json")):
        stem = Path(f).stem
        names.add(re.sub(r"_sample_\d+$", "", stem))
    return sorted(names)


def print_report(reports: list[dict]) -> None:
    header = f"{'Model':<22}{'Strict F1':>11}{'Strict P/R':>14}{'Fuzzy F1':>11}{'Fuzzy P/R':>13}{'Date hallucin.':>16}"
    print(header)
    print("-" * len(header))
    for r in reports:
        s, fz = r["aggregate"]["strict"], r["aggregate"]["fuzzy"]
        strict_pr = f"{s['precision']:.2f}/{s['recall']:.2f}"
        fuzzy_pr = f"{fz['precision']:.2f}/{fz['recall']:.2f}"
        date_pct = f"{r['date_hallucination_rate'] * 100:.0f}%"
        print(
            f"{r['model']:<22}{s['f1']:>11.3f}{strict_pr:>14}"
            f"{fz['f1']:>11.3f}{fuzzy_pr:>13}{date_pct:>16}"
        )
    print()
    print("说明：Strict = 归一化后逐字相等；Fuzzy = 允许用词差异（difflib 相似度 >= "
          f"{FUZZY_THRESHOLD}）。Date hallucin. = 原文没提年份，模型却编了年份的样本占比。")


if __name__ == "__main__":
    models = discover_models()
    if not models:
        raise SystemExit(f"没在 {RESULTS_DIR} 找到任何 *_sample_*.json，先跑 compare_models.py")

    reports = [evaluate_model(m) for m in models]
    print_report(reports)

    print("\n=== 逐样本明细（漏抽/编造的具体地点名） ===")
    for r in reports:
        print(f"\n{r['model']}:")
        for s in r["per_sample"]:
            strict = s["match"]["strict"]
            if strict["missed"] or strict["fabricated"]:
                print(f"  {s['sample']} (strict): missed={strict['missed']} fabricated={strict['fabricated']}")
            else:
                print(f"  {s['sample']} (strict): clean")
