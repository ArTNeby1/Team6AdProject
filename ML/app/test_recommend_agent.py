"""
自测脚本：证明 recommend_grounded() 的"安全网"真的挡得住模型编造。

F-18 的核心承诺是"不会推荐出不存在的地方"——代码上靠的是 recommend_agent.py
里那段回查候选表、名字对不上就丢弃的逻辑（见 recommend_grounded() 里
"关键一步"那段注释）。但这段逻辑之前只在 __main__ 里跑过手动冒烟测试，
需要本机真的起着 Ollama 才能测，CI 里跑不了，等于这个"安全网"本身没有
自动化证据证明它真的在拦。

做法：把 recommend_agent.call_local_model 换成假函数，直接返回写死的 JSON，
不需要 Ollama、不需要联网。候选列表用 content_recommender 在真实数据集上
查出来的真实结果（不是编的），这样"合法候选"和"编造的名字"的边界是真的，
不是自己既当裁判又当选手。

跑法（项目根目录 Team6AdProject 下）：
    python ML/app/test_recommend_agent.py
"""
import json
import sys
from pathlib import Path

# Windows 终端默认 GBK，数据集里的商标符号（Marina Bay Sands®）打不出来会直接崩溃；
# 这里改成宽松模式（乱码但不崩），跟 geo.py 里"避免打印非 ASCII"是同一个问题的另一种解法。
sys.stdout.reconfigure(encoding="utf-8", errors="backslashreplace")

APP_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(APP_DIR))

import recommend_agent  # noqa: E402
from content_recommender import recommend_from_dataset  # noqa: E402


def expect(name, condition):
    print(f"[{'PASS' if condition else 'FAIL'}] {name}")


TRIP = {
    "destination": "Singapore",
    "dates": [],
    "places": [
        {"name": "Gardens by the Bay", "type": "attraction",
         "lat": 1.2816, "lng": 103.8636, "activities": ["Cloud Forest dome"]},
    ],
}


def _real_candidates(top_n=10):
    """从真实数据集查一次候选，拿真名字来造测试数据，不是自己编的。"""
    return recommend_from_dataset(TRIP, top_n=top_n)


def _fake_llm_response(recommended: list[dict]):
    """造一份符合 RecommendationResult schema 的假 LLM 输出。"""
    return json.dumps(
        {
            "destination": "Singapore",
            "based_on": ["Gardens by the Bay"],
            "recommended": recommended,
        }
    )


def patch_llm(fake_response_text: str):
    """把 recommend_agent 模块里绑定的 call_local_model 换掉，返回还原用的原函数。"""
    original = recommend_agent.call_local_model
    recommend_agent.call_local_model = lambda *a, **k: fake_response_text
    return original


def test_invented_place_is_dropped():
    candidates = _real_candidates()
    real_name = candidates[0]["name"]
    fake_text = _fake_llm_response(
        [
            {"name": real_name, "type": candidates[0]["type"],
             "reason": "Matches your interest.", "activities": []},
            {"name": "Underwater City of Atlantis", "type": "attraction",
             "reason": "A place I made up.", "activities": []},
        ]
    )
    original = patch_llm(fake_text)
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=5)
        names = [r["name"] for r in result]
        expect(f"编造的地点被丢弃（实际返回 {names}）", "Underwater City of Atlantis" not in names)
        expect(f"真实候选被保留（实际返回 {names}）", real_name in names)
        expect("结果只剩 1 条（丢了 1 个编造的）", len(result) == 1)
    finally:
        recommend_agent.call_local_model = original


def test_trademark_symbol_variant_still_matches():
    # 真实数据集里有 "Marina Bay Sands®"，模型抄写时几乎必丢商标符号——
    # 这不该被当成编造，归一化匹配要能认出这是同一个地方。
    candidates = _real_candidates(top_n=30)
    source = next((c for c in candidates if c["name"].strip().rstrip("®") == "Marina Bay Sands"), None)
    if source is None:
        print("[SKIP] 'Marina Bay Sands®' 不在这次候选池里（候选池是动态排序的），跳过")
        return

    fake_text = _fake_llm_response(
        [{"name": "Marina Bay Sands", "type": source["type"],  # 模型输出：没有 ® 符号
          "reason": "Iconic landmark nearby.", "activities": []}]
    )
    original = patch_llm(fake_text)
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=5, candidate_pool=30)
        expect("去掉商标符号的写法仍然匹配到候选", len(result) == 1)
        if result:
            expect(
                f"返回的 name 以数据集为准，带回 ® 符号（实际 {result[0]['name']!r}）",
                result[0]["name"] == source["name"],
            )
    finally:
        recommend_agent.call_local_model = original


def test_coords_come_from_dataset_not_model():
    # 模型不允许影响坐标——即使模型在 JSON 里没给坐标字段（schema 也没要求它给），
    # 返回的 lat/lng 必须原样来自数据集，不能是 None 或模型编的。
    candidates = _real_candidates()
    real = candidates[0]
    fake_text = _fake_llm_response(
        [{"name": real["name"], "type": real["type"], "reason": "test", "activities": []}]
    )
    original = patch_llm(fake_text)
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=1)
        expect(f"lat 来自数据集（实际 {result[0]['lat']}，期望 {real['lat']}）", result[0]["lat"] == real["lat"])
        expect(f"lng 来自数据集（实际 {result[0]['lng']}，期望 {real['lng']}）", result[0]["lng"] == real["lng"])
    finally:
        recommend_agent.call_local_model = original


def test_top_n_is_respected_even_with_extra_valid_picks():
    # 模型如果不听话多给了几个（即使都合法），也要在 top_n 处截断
    candidates = _real_candidates(top_n=10)
    picks = [
        {"name": c["name"], "type": c["type"], "reason": "ok", "activities": []}
        for c in candidates[:5]
    ]
    fake_text = _fake_llm_response(picks)
    original = patch_llm(fake_text)
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=2, candidate_pool=10)
        expect(f"top_n=2 时最多返回 2 条（实际 {len(result)}）", len(result) == 2)
    finally:
        recommend_agent.call_local_model = original


def test_all_invented_returns_empty_not_error():
    # 模型如果整份输出全是编的，不该报错，应该干干净净返回空列表
    fake_text = _fake_llm_response(
        [{"name": "Made Up Place One", "type": "attraction", "reason": "x", "activities": []},
         {"name": "Made Up Place Two", "type": "attraction", "reason": "y", "activities": []}]
    )
    original = patch_llm(fake_text)
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=5)
        expect("全部编造时不报错，返回空列表", result == [])
    finally:
        recommend_agent.call_local_model = original


def test_no_candidates_skips_llm_call():
    # 候选池为空（比如 max_distance_km 卡得太死）时，不该白调一次 LLM
    original = patch_llm(_fake_llm_response([]))
    call_count = {"n": 0}
    real_fn = recommend_agent.call_local_model

    def counting_fn(*a, **k):
        call_count["n"] += 1
        return real_fn(*a, **k)

    recommend_agent.call_local_model = counting_fn
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=3, max_distance_km=0.0001)
        expect("候选为空时直接返回空列表", result == [])
        expect(f"候选为空时不调用 LLM（实际调用 {call_count['n']} 次）", call_count["n"] == 0)
    finally:
        recommend_agent.call_local_model = original


def test_mock_provider_returns_real_places_with_mock_reason():
    # RECOMMEND_PROVIDER=mock 时应该完全跳过 LLM，但地点/坐标/距离仍是真实数据集的
    original_provider = recommend_agent.RECOMMEND_PROVIDER
    original_llm = recommend_agent.call_local_model
    call_count = {"n": 0}
    recommend_agent.call_local_model = lambda *a, **k: call_count.__setitem__("n", call_count["n"] + 1) or "{}"
    recommend_agent.RECOMMEND_PROVIDER = "mock"
    try:
        result = recommend_agent.recommend_grounded(TRIP, top_n=3)
        expect(f"mock 模式返回 3 条（实际 {len(result)}）", len(result) == 3)
        expect("mock 模式不调用 LLM", call_count["n"] == 0)
        expect("mock 模式的 reason 都带 [MOCK] 前缀", all(r["reason"].startswith("[MOCK]") for r in result))
        expect("mock 模式的坐标仍是真实数据（不是 None）", all(r["lat"] is not None for r in result))
    finally:
        recommend_agent.RECOMMEND_PROVIDER = original_provider
        recommend_agent.call_local_model = original_llm


if __name__ == "__main__":
    test_invented_place_is_dropped()
    test_trademark_symbol_variant_still_matches()
    test_coords_come_from_dataset_not_model()
    test_top_n_is_respected_even_with_extra_valid_picks()
    test_all_invented_returns_empty_not_error()
    test_no_candidates_skips_llm_call()
    test_mock_provider_returns_real_places_with_mock_reason()
