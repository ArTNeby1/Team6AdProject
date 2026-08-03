"""
演示脚本：完整跑一遍三段式 pipeline（降噪 -> 抽取 -> 推荐），答辩/演示时直接跑
这一个文件就行，不用现场敲一堆命令，也不用挨个启动 uvicorn。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/demo_pipeline.py            # 演示单段文本（不经过降噪）
    python ML/app/demo_pipeline.py --messages  # 演示多轮聊天 + chat_filter 降噪

前提：本机 Ollama 已经在跑（装好之后是常驻后台服务），且已经 pull 好
EXTRACT_MODEL / RECOMMEND_MODEL 对应的模型（默认都是
llama3.1:8b-instruct-q4_K_M，本机已有）。

这台开发机被迫用 CPU 推理（GPU 兼容性问题，见 local_llm_client.py 里
DEFAULT_NUM_GPU 的说明），完整跑一次大概 1.5~2 分钟，演示前建议先跑一遍
心里有数，别当场干等。
"""
import argparse
import sys
import time
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from orchestrator import run_pipeline  # noqa: E402

SAMPLES_DIR = Path(__file__).resolve().parent.parent / "samples"

DEMO_MESSAGES = [
    {"role": "user", "content": "嗨你好呀！"},
    {"role": "user", "content": "我想去新加坡玩，主要想看看Gardens by the Bay，然后想吃chicken rice"},
    {"role": "assistant", "content": "好的，没问题！"},
    {"role": "user", "content": "哈哈谢谢，就这样吧"},
]


def main():
    parser = argparse.ArgumentParser(description="演示 chat_filter -> extract -> recommend 三段式 pipeline")
    parser.add_argument(
        "--messages", action="store_true",
        help="演示多轮聊天 + 降噪路径（对应 /refine 接口），不加这个参数就演示单段文本（对应 /extract-travel-info）",
    )
    args = parser.parse_args()

    print("=" * 60)
    if args.messages:
        print("输入：多轮聊天记录（会先过 chat_filter 降噪）")
        for m in DEMO_MESSAGES:
            print(f"  [{m['role']}] {m['content']}")
        kwargs = {"messages": DEMO_MESSAGES, "source_name": "demo_chat"}
    else:
        text = (SAMPLES_DIR / "sample_1.txt").read_text(encoding="utf-8")
        print("输入：单段文本 samples/sample_1.txt（不经过降噪，直接抽取）")
        print(text[:200] + ("..." if len(text) > 200 else ""))
        kwargs = {"raw_content": text, "source_name": "sample_1.txt"}
    print("=" * 60)

    start = time.time()
    result = run_pipeline(**kwargs)
    elapsed = time.time() - start

    if result.error:
        print(f"\n[FAIL] pipeline 出错: {result.error}")
        print(f"耗时: {elapsed:.1f}s")
        return

    if args.messages:
        print("\n[① chat_filter 降噪后的文字]")
        print(result.cleaned_text or "(过滤后为空)")

    print(f"\n[② 抽取结果] destination = {result.extraction.destination}")
    for p in result.extraction.places:
        print(f"  - {p.name} ({p.type}): {p.activities}")

    print("\n[③ 推荐结果] —— 这部分才是设计上要传给后端的东西")
    for p in result.recommendation.recommended:
        print(f"  - {p.name} ({p.type})")
        print(f"    理由: {p.reason}")

    print(f"\n总耗时: {elapsed:.1f}s")


if __name__ == "__main__":
    main()
