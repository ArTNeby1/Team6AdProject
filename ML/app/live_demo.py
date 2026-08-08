"""
答辩现场演示脚本：当场换一句偏好文字，立刻看推荐结果怎么变。
用来证明"偏好真的影响推荐排序"，而不是提前写死的结果。

跑法（项目根目录 Team6AdProject 下）：
    python -X utf8 ML/app/live_demo.py "museums, history, culture, temples"
    python -X utf8 ML/app/live_demo.py "beach, nature, park"
    python -X utf8 ML/app/live_demo.py
（不给参数就是不带偏好，只看"去过 Gardens by the Bay"这一条信息推荐）

第二个参数可以切排序模式，用来演示 F-18 的 "nearby or similar"：
    python -X utf8 ML/app/live_demo.py "museums" similar   # 只看文字相似
    python -X utf8 ML/app/live_demo.py "museums" nearby    # 只看距离
    python -X utf8 ML/app/live_demo.py "museums" hybrid    # 两者结合（默认）
偏好文字用英文：数据集里的 description 是英文，中文偏好匹配不上任何词。
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from content_recommender import recommend_from_dataset  # noqa: E402

# 带上坐标，这样也能顺便演示 nearby（距离）那一半；
# 真实流程里坐标是后端地理编码补的，抽取阶段是 null。
demo_trip = {
    "destination": "Singapore",
    "places": [
        {
            "name": "Gardens by the Bay",
            "type": "attraction",
            "lat": 1.2816,
            "lng": 103.8636,
            "activities": ["Cloud Forest dome"],
        },
    ],
}

preference = sys.argv[1] if len(sys.argv) > 1 else None
mode = sys.argv[2] if len(sys.argv) > 2 else "hybrid"

print(f"preference: {preference!r}   mode: {mode}")
print(f"{'similarity':>10s}  {'distance':>9s}  place")
for r in recommend_from_dataset(demo_trip, preference_text=preference, top_n=5, mode=mode):
    dist = f"{r['distance_km']}km" if r["distance_km"] is not None else "n/a"
    print(f"{r['similarity']:>10.3f}  {dist:>9s}  {r['name']}")
