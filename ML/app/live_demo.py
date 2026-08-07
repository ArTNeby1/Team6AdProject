"""
答辩现场演示脚本：当场换一句偏好文字，立刻看推荐结果怎么变。
用来证明"偏好真的影响推荐排序"，而不是提前写死的结果。

跑法（项目根目录 Team6AdProject 下）：
    python -X utf8 ML/app/live_demo.py "museums, history, culture, temples"
    python -X utf8 ML/app/live_demo.py "beach, nature, park"
    python -X utf8 ML/app/live_demo.py
（不给参数就是不带偏好，只看"去过 Gardens by the Bay"这一条信息推荐）
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from content_recommender import recommend_from_dataset  # noqa: E402

demo_trip = {
    "destination": "Singapore",
    "places": [
        {"name": "Gardens by the Bay", "type": "attraction", "activities": ["Cloud Forest dome"]},
    ],
}

preference = sys.argv[1] if len(sys.argv) > 1 else None
print(f"偏好文字：{preference!r}")
for r in recommend_from_dataset(demo_trip, preference_text=preference, top_n=5):
    print(f"{r['similarity']:.3f}  {r['name']}")
