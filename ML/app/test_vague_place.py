"""
自测脚本：证明 vague_place.py（"夜市"这种笼统地点名换成具体候选）真的能跑、
结果符合基本预期。不依赖 pytest/Ollama/AWS，跟 test_content_recommender.py
一个风格。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_vague_place.py
"""
import sys
from pathlib import Path

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

from vague_place import find_vague_places, resolve_vague_place_suggestions  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")
    assert condition, name


def test_detects_english_and_chinese_vague_names():
    places = [
        {"name": "Gardens by the Bay", "type": "attraction"},
        {"name": "Night Market", "type": "market"},  # 大小写不敏感
        {"name": "夜市", "type": "other"},
    ]
    found = find_vague_places(places)
    expect(f"detects 2 vague entries (actual {len(found)})", len(found) == 2)
    expect(
        "does not flag a specific place name",
        all(f["original_name"] != "Gardens by the Bay" for f in found),
    )


def test_specific_market_name_not_flagged():
    # 用户如果直接说了具体的夜市名字，不该被当成"笼统类别"拦下来。
    places = [{"name": "Bugis Street Market", "type": "market"}]
    expect("specific market name is not treated as vague", find_vague_places(places) == [])


def test_resolves_night_market_to_real_candidates():
    places = [{"name": "night market", "type": "market"}]
    suggestions = resolve_vague_place_suggestions(places, top_n=3)
    expect(f"returns at least 1 candidate (actual {len(suggestions)})", len(suggestions) >= 1)
    expect(
        "every candidate has real coordinates (not null)",
        all(s["lat"] is not None and s["lng"] is not None for s in suggestions),
    )
    expect(
        "every candidate's reason mentions the original vague name",
        all("night market" in s["reason"].lower() for s in suggestions),
    )
    names = {s["name"] for s in suggestions}
    expect(f"includes a known real night market (got {names})", "Bugis Street Market" in names or "Chinatown Street Market" in names)


def test_no_vague_places_returns_empty():
    places = [{"name": "Gardens by the Bay", "type": "attraction"}]
    expect("no vague places -> no extra suggestions", resolve_vague_place_suggestions(places) == [])


def test_duplicate_category_not_queried_twice():
    # 同一个 category（比如中英文各说一次"夜市"）只应该查一次候选表，
    # 不会因为出现两条笼统地点就把同一批候选重复拼两遍。
    places = [{"name": "night market", "type": "market"}, {"name": "夜市", "type": "market"}]
    suggestions = resolve_vague_place_suggestions(places, top_n=3)
    names = [s["name"] for s in suggestions]
    expect(f"no duplicate names in result ({names})", len(names) == len(set(names)))


def test_nearby_user_place_sorts_candidates_by_distance():
    # 用户已确认地点离 Chinatown Street Market 更近时，它应该排在
    # Bugis Street Market 前面——证明真的按距离排序，不是固定顺序。
    near_chinatown = [
        {"name": "Sri Mariamman Temple: Hindu Temple in Singapore", "type": "attraction", "lat": 1.2820, "lng": 103.8454},
        {"name": "night market", "type": "market"},
    ]
    suggestions = resolve_vague_place_suggestions(near_chinatown, top_n=2)
    names = [s["name"] for s in suggestions]
    expect(f"closer market ranked first (got {names})", names[0] == "Chinatown Street Market")


if __name__ == "__main__":
    test_detects_english_and_chinese_vague_names()
    test_specific_market_name_not_flagged()
    test_resolves_night_market_to_real_candidates()
    test_no_vague_places_returns_empty()
    test_duplicate_category_not_queried_twice()
    test_nearby_user_place_sorts_candidates_by_distance()
