"""
自测脚本：geo.py（haversine 直线距离 + "离已确认地点最近距离"）。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_geo.py
不需要联网/Ollama/AWS，纯本地数学计算。
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from geo import haversine_km, min_distance_to_places  # noqa: E402

GARDENS = (1.2816, 103.8636)
MUSEUM = (1.2966, 103.8485)
SENTOSA = (1.2494, 103.8303)


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


def test_same_point_is_zero():
    expect("同一点距离为 0", haversine_km(*GARDENS, *GARDENS) == 0.0)


def test_symmetric():
    a = haversine_km(*GARDENS, *MUSEUM)
    b = haversine_km(*MUSEUM, *GARDENS)
    expect(f"A->B 和 B->A 距离相等 ({a} vs {b})", abs(a - b) < 1e-9)


def test_known_distance_ballpark():
    # 滨海湾花园到国家博物馆实际路网约 3~4km，直线距离应该在合理范围内（不做到米级精确校验）
    dist = haversine_km(*GARDENS, *MUSEUM)
    expect(f"滨海湾花园->国家博物馆直线距离在 1~6km 之间（实际 {dist:.2f}km）", 1.0 < dist < 6.0)


def test_min_distance_picks_nearest_not_average():
    # 圣淘沙离国家博物馆比离滨海湾花园远，min 应该挑离得更近的滨海湾花园
    places = [{"lat": GARDENS[0], "lng": GARDENS[1]}, {"lat": MUSEUM[0], "lng": MUSEUM[1]}]
    got = min_distance_to_places(*SENTOSA, places)
    direct_to_gardens = haversine_km(*SENTOSA, *GARDENS)
    expect(f"min_distance 等于最近那个点的距离（{got} vs {direct_to_gardens}）",
           abs(got - direct_to_gardens) < 1e-9)


def test_skips_places_missing_coords():
    places = [{"lat": None, "lng": None}, {"lat": GARDENS[0], "lng": GARDENS[1]}]
    got = min_distance_to_places(*SENTOSA, places)
    expect("缺坐标的条目被跳过，仍能用剩下那个算出距离", got is not None)


def test_no_usable_coords_returns_none():
    places = [{"lat": None, "lng": None}]
    got = min_distance_to_places(*SENTOSA, places)
    expect("一个能用的坐标都没有时返回 None", got is None)


def test_empty_places_returns_none():
    expect("places 为空列表时返回 None", min_distance_to_places(*SENTOSA, []) is None)


if __name__ == "__main__":
    test_same_point_is_zero()
    test_symmetric()
    test_known_distance_ballpark()
    test_min_distance_picks_nearest_not_average()
    test_skips_places_missing_coords()
    test_no_usable_coords_returns_none()
    test_empty_places_returns_none()
