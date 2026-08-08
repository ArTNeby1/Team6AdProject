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
    expect(f"dataset has at least 50 rows (actual {len(df)})", len(df) >= 50)
    expect("dataset has name/description columns", {"name", "description"}.issubset(df.columns))


def test_excludes_already_visited():
    # National Gallery Singapore 是数据集里真实存在的一条，放进"已规划地点"里，
    # 推荐结果不应该原样把它再推荐一次。
    trip = {
        "destination": "Singapore",
        "places": [{"name": "National Gallery Singapore", "type": "attraction", "activities": ["see exhibitions"]}],
    }
    results = recommend_from_dataset(trip, top_n=5)
    names = [r["name"].strip().lower() for r in results]
    expect("recommendations exclude already-planned places", "national gallery singapore" not in names)
    expect(f"result count <= top_n (actual {len(results)})", len(results) <= 5)


def test_results_sorted_by_similarity_desc():
    trip = {"destination": "Singapore", "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}]}
    results = recommend_from_dataset(trip, top_n=10)
    scores = [r["similarity"] for r in results]
    expect("results sorted by similarity desc", scores == sorted(scores, reverse=True))


def test_preference_text_changes_ranking():
    # 同一个 trip，不给偏好 vs 给"museum history culture"偏好，Top1 应该不一样，
    # 证明偏好文字真的参与了相似度计算，不是摆设。
    trip = {"destination": "Singapore", "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}]}
    no_pref = recommend_from_dataset(trip, top_n=1)
    with_pref = recommend_from_dataset(trip, preference_text="museum history culture temple", top_n=1)
    expect(
        f"preference text changes Top1 (without: {no_pref[0]['name']!r}, with: {with_pref[0]['name']!r})",
        no_pref[0]["name"] != with_pref[0]["name"],
    )


def test_nearby_mode_sorts_by_distance():
    # 带坐标时 nearby 模式应该按距离升序，且每条都带 distance_km。
    trip = {
        "destination": "Singapore",
        "places": [{"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": []}],
    }
    results = recommend_from_dataset(trip, top_n=5, mode="nearby")
    distances = [r["distance_km"] for r in results]
    expect("nearby mode returns distance_km on every row", all(d is not None for d in distances))
    expect(f"nearby mode sorted by distance asc ({distances})", distances == sorted(distances))


def test_hybrid_penalises_far_places():
    # 同一个 trip：HortPark 文字相似度高但有 7km 远，纯相似度模式会进 Top3，
    # hybrid 模式应该把它挤下去——证明距离真的参与了打分，不是摆设。
    trip = {
        "destination": "Singapore",
        "places": [{"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["photos"]}],
    }
    similar_top3 = [r["name"] for r in recommend_from_dataset(trip, top_n=3, mode="similar")]
    hybrid_top3 = [r["name"] for r in recommend_from_dataset(trip, top_n=3, mode="hybrid")]
    expect(f"hybrid Top3 differs from similar (similar includes far-away {similar_top3[1]!r})", similar_top3 != hybrid_top3)


def test_max_distance_filter():
    trip = {
        "destination": "Singapore",
        "places": [{"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": []}],
    }
    results = recommend_from_dataset(trip, top_n=20, mode="hybrid", max_distance_km=3.0)
    expect(f"max_distance_km=3 keeps everything within 3km ({len(results)} rows)", all(r["distance_km"] <= 3.0 for r in results))


def test_no_coords_falls_back_to_similarity():
    # 用户地点没有 lat/lng（抽取阶段 coords 就是 null）时，不能崩，
    # 应该退回纯相似度排序，distance_km 为 None。
    trip = {"destination": "Singapore", "places": [{"name": "Gardens by the Bay", "type": "attraction", "activities": []}]}
    results = recommend_from_dataset(trip, top_n=3, mode="hybrid")
    expect("no coords: does not crash and still returns results", len(results) > 0)
    expect("no coords: distance_km is None", all(r["distance_km"] is None for r in results))


if __name__ == "__main__":
    test_dataset_loaded()
    test_excludes_already_visited()
    test_results_sorted_by_similarity_desc()
    test_preference_text_changes_ranking()
    test_nearby_mode_sorts_by_distance()
    test_hybrid_penalises_far_places()
    test_max_distance_filter()
    test_no_coords_falls_back_to_similarity()
