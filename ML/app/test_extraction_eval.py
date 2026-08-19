"""extraction_eval 的单元测试：记分卡数学 + gold 解析/兜底，都是纯函数，不碰模型。"""
import extraction_eval as ee


def test_score_perfect_extraction():
    src = "Day 1: Gardens by the Bay. Day 2: Merlion Park."
    gold = ["Gardens by the Bay", "Merlion Park"]
    pred = ["Gardens by the Bay", "Merlion Park"]
    result = ee.score(src, pred, gold)
    assert result["precision"] == 1.0
    assert result["recall"] == 1.0
    assert result["f1"] == 1.0
    assert result["groundedness"] == 1.0
    assert result["missed"] == []
    assert result["spurious"] == []


def test_score_missed_places_lower_recall():
    src = "Gardens by the Bay, Merlion Park, Sentosa"
    gold = ["Gardens by the Bay", "Merlion Park", "Sentosa"]
    pred = ["Gardens by the Bay"]  # 只抽到 1/3
    result = ee.score(src, pred, gold)
    assert result["precision"] == 1.0            # 抽出的那个是对的
    assert result["recall"] == round(1 / 3, 4)   # 3 个真实地点只抓到 1 个
    assert set(result["missed"]) == {"Merlion Park", "Sentosa"}


def test_score_hallucination_hits_precision_and_groundedness():
    src = "We visited Merlion Park."
    gold = ["Merlion Park"]
    pred = ["Merlion Park", "Atlantis Resort"]  # 第二个是编造的，原文里没有
    result = ee.score(src, pred, gold)
    assert result["precision"] == 0.5            # 2 个里 1 个是编造 -> spurious
    assert result["groundedness"] == 0.5         # 编造的那个原文里没有
    assert result["spurious"] == ["Atlantis Resort"]


def test_score_fuzzy_matching_tolerates_case_and_substring():
    src = "marina bay sands skypark observation deck"
    gold = ["Marina Bay Sands SkyPark Observation Deck"]
    pred = ["marina bay sands"]  # 大小写不同 + 只是子串
    result = ee.score(src, pred, gold)
    assert result["recall"] == 1.0
    assert result["matched"] == ["Marina Bay Sands SkyPark Observation Deck"]


def test_score_empty_predictions_are_all_zero_not_crash():
    result = ee.score("anything", [], ["Merlion Park"])
    assert result["precision"] == 0.0
    assert result["recall"] == 0.0
    assert result["f1"] == 0.0
    assert result["groundedness"] == 0.0


def test_heuristic_gold_picks_capitalized_multiword_phrases():
    gold = ee.heuristic_gold("We went to Merlion Park and Marina Bay Sands today.")
    assert "Merlion Park" in gold
    assert "Marina Bay Sands" in gold


def test_parse_gold_accepts_string_and_object_places():
    assert ee.parse_gold('{"places": ["Merlion Park", {"name": "Vivo City"}]}') == [
        "Merlion Park",
        "Vivo City",
    ]


def test_parse_gold_recovers_json_wrapped_in_prose():
    raw = 'Sure! Here are the places: {"places": ["Sentosa"]} hope that helps'
    assert ee.parse_gold(raw) == ["Sentosa"]


def test_parse_gold_returns_empty_on_garbage():
    assert ee.parse_gold("not json at all") == []
    assert ee.parse_gold("") == []
