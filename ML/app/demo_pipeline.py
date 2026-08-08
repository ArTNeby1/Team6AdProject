"""
演示脚本：完整跑一遍产品流程（降噪 -> 抽取 -> 用户确认 -> 推荐），
答辩/演示时直接跑这一个文件就行，不用现场敲一堆命令，也不用启动 uvicorn。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/demo_pipeline.py            # 演示单段文本（不经过降噪）
    python ML/app/demo_pipeline.py --messages  # 演示多轮聊天 + chat_filter 降噪

**这个脚本演示的是拆开之后的两步流程**（2026-08-08 改）：
先 run_extraction() 解析，停下来模拟用户确认，确认之后才 run_recommendation()。
这跟线上接口的行为一致（/extract-travel-info 和 /recommend 是两个独立接口），
不要再用一次跑完的 run_pipeline()——那个已经弃用了。

演示数据一律用英文：实测本地 8B 量化模型对中文输入会吐乱码地名
（见 ML/docs/ai_contract.md 第 8 节），英文正常。用中文演示会让人误以为
流程有 bug，其实是模型的语言能力限制。

前提：本机 Ollama 已经在跑（装好之后是常驻后台服务），且已经 pull 好
EXTRACT_MODEL / RECOMMEND_MODEL 对应的模型（默认都是
llama3.1:8b-instruct-q4_K_M，本机已有）。
"""
import argparse
import os
import sys
import time
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from orchestrator import run_extraction, run_recommendation  # noqa: E402


def _print_active_extract_model():
    """演示前先把实际会用哪个模型打印出来，避免现场忘了设环境变量、
    结果用了默认的本地 Ollama 却以为在跑 Bedrock Nova Lite。"""
    provider = os.environ.get("EXTRACT_PROVIDER", "ollama")
    if provider == "bedrock":
        from bedrock_client import MODEL_ID
        profile = os.environ.get("AWS_PROFILE", "not set, using default credentials")
        print(f"[extract model] Bedrock -> {MODEL_ID} (AWS_PROFILE={profile})")
    else:
        from local_llm_client import DEFAULT_EXTRACT_MODEL
        print(f"[extract model] local Ollama -> {DEFAULT_EXTRACT_MODEL}")


SAMPLES_DIR = Path(__file__).resolve().parent.parent / "samples"

DEMO_MESSAGES = [
    {"role": "user", "content": "hey there!"},
    {"role": "user", "content": "I want to visit Singapore, mainly to see Gardens by the Bay, and I'd like to eat chicken rice"},
    {"role": "assistant", "content": "Sure, no problem!"},
    {"role": "user", "content": "haha thanks, that's all"},
]

# 模拟后端地理编码的结果：抽取阶段 coords 一定是 null，真实坐标由后端查地图 API 补。
# 演示时没有后端，就用这张小表按名字补一下，好让推荐阶段能算距离（nearby）。
DEMO_GEOCODE = {
    "gardens by the bay": (1.2816, 103.8636),
    "marina bay sands": (1.2830, 103.8585),
    "merlion park": (1.2868, 103.8545),
    "chinatown": (1.2812, 103.8443),
    "sentosa": (1.2494, 103.8303),
    "universal studios singapore": (1.2540, 103.8238),
}


def _fake_geocode(places: list[dict]) -> list[dict]:
    """把抽取结果里的 coords: null 换成 lat/lng，模拟后端 MapPlacesClient 做的事。
    查不到的地点就不给坐标——推荐阶段会自动退回纯文本相似度，不会报错。"""
    out = []
    for p in places:
        coords = DEMO_GEOCODE.get(p["name"].strip().lower())
        out.append({
            "name": p["name"],
            "type": p["type"],
            "activities": p.get("activities", []),
            "lat": coords[0] if coords else None,
            "lng": coords[1] if coords else None,
        })
    return out


def main():
    parser = argparse.ArgumentParser(description="Demo: extract -> confirm -> recommend")
    parser.add_argument(
        "--messages", action="store_true",
        help="demo the multi-turn chat path (matches /refine); without it, demo single text (matches /extract-travel-info)",
    )
    args = parser.parse_args()

    print("=" * 66)
    _print_active_extract_model()
    print("=" * 66)

    if args.messages:
        print("INPUT: multi-turn chat (goes through chat_filter first)")
        for m in DEMO_MESSAGES:
            print(f"  [{m['role']}] {m['content']}")
        kwargs = {"messages": DEMO_MESSAGES, "source_name": "demo_chat"}
    else:
        text = (SAMPLES_DIR / "sample_1.txt").read_text(encoding="utf-8")
        print("INPUT: single text samples/sample_1.txt (no noise filtering, straight to extraction)")
        print(text[:200] + ("..." if len(text) > 200 else ""))
        kwargs = {"raw_content": text, "source_name": "sample_1.txt"}
    print("=" * 66)

    # ---------- 第一步：解析（对应 POST /extract-travel-info 或 /refine）----------
    start = time.time()
    result = run_extraction(**kwargs)
    extract_time = time.time() - start

    if result.error:
        print(f"\n[FAIL] extraction failed: {result.error}")
        print(f"elapsed: {extract_time:.1f}s")
        return

    if args.messages:
        print("\n[STEP 1a] chat_filter output (small talk removed)")
        print(result.cleaned_text or "(empty after filtering)")

    print(f"\n[STEP 1b] EXTRACTION -> destination = {result.extraction.destination}")
    for p in result.extraction.places:
        print(f"  - {p.name} ({p.type}): {p.activities}  coords={p.coords}")
    print(f"  extraction took {extract_time:.1f}s")

    # ---------- 中间：用户确认（真实流程里是前端展示 + 用户点确认）----------
    print("\n" + "-" * 66)
    print("[STEP 2] USER CONFIRMS  <- in the real app the user edits/approves here.")
    print("         Backend also geocodes the places at this point (coords: null -> real lat/lng).")
    print("-" * 66)

    confirmed = _fake_geocode([p.model_dump() for p in result.extraction.places])
    for p in confirmed:
        print(f"  - {p['name']:42s} lat={p['lat']} lng={p['lng']}")

    # ---------- 第二步：推荐（对应 POST /recommend）----------
    start = time.time()
    recommended = run_recommendation(
        places=confirmed,
        destination=result.extraction.destination,
        top_n=3,
    )
    recommend_time = time.time() - start

    print("\n[STEP 3] RECOMMENDATION (F-18) -- this is what gets stored by the backend")
    if not recommended:
        print("  (no recommendations returned)")
    for p in recommended:
        dist = f"{p['distance_km']}km away" if p["distance_km"] is not None else "distance unknown"
        print(f"  - {p['name']} ({p['type']}, {dist})")
        print(f"    reason: {p['reason']}")

    print(f"\n  recommendation took {recommend_time:.1f}s")
    print(f"total: {extract_time + recommend_time:.1f}s")


if __name__ == "__main__":
    main()
