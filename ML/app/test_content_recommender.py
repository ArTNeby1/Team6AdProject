"""
自测脚本：证明 content_recommender.py 这个 TF-IDF 推荐原型真的能跑、结果符合
基本预期（不是空口说"能用"）。不依赖 pytest，跟 test_robustness.py 一个风格。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_content_recommender.py
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from content_recommender import _load_dataset, recommend_from_dataset  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")


def test_dataset_loaded():
    df = _load_dataset()
    expect(f"数据集至少有 50 条（实际 {len(df)}）", len(df) >= 50)
    expect("数据集有 name/description 列", {"name", "description"}.issubset(df.columns))


def test_excludes_already_visited():
    # National Gallery Singapore 是数据集里真实存在的一条，放进"已规划地点"里，
    # 推荐结果不应该原样把它再推荐一次。
    trip = {
        "destination": "Singapore",
        "places": [{"name": "National Gallery Singapore", "type": "attraction", "activities": ["看展"]}],
    }
    results = recommend_from_dataset(trip, top_n=5)
    names = [r["name"].strip().lower() for r in results]
    expect("推荐结果里不包含已规划的地点", "national gallery singapore" not in names)
    expect(f"返回数量 <= top_n（实际 {len(results)}）", len(results) <= 5)


def test_results_sorted_by_similarity_desc():
    trip = {"destination": "Singapore", "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}]}
    results = recommend_from_dataset(trip, top_n=10)
    scores = [r["similarity"] for r in results]
    expect("结果按相似度从高到低排序", scores == sorted(scores, reverse=True))


def test_preference_text_changes_ranking():
    # 同一个 trip，不给偏好 vs 给"museum history culture"偏好，Top1 应该不一样，
    # 证明偏好文字真的参与了相似度计算，不是摆设。
    trip = {"destination": "Singapore", "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}]}
    no_pref = recommend_from_dataset(trip, top_n=1)
    with_pref = recommend_from_dataset(trip, preference_text="museum history culture temple", top_n=1)
    expect(
        f"加偏好文字后 Top1 推荐会变化（无偏好: {no_pref[0]['name']!r}，有偏好: {with_pref[0]['name']!r}）",
        no_pref[0]["name"] != with_pref[0]["name"],
    )


if __name__ == "__main__":
    test_dataset_loaded()
    test_excludes_already_visited()
    test_results_sorted_by_similarity_desc()
    test_preference_text_changes_ranking()
