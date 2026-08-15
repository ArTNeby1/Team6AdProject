"""
识别"用户说的地点其实是个笼统类别，不是具体地点"这种情况（F-18 的一个小延伸，
answering 组里提的需求：用户说"夜市"这种不具体的地点时，主动推荐几个具体的
真实夜市）。

场景：用户说"晚上去逛夜市"，抽取阶段只能老实抽出 name="night market"
（或"夜市"，见 schema/trip_models.py 的 Place.name）——这不是一个能拿去查
地图 API 的具体地址，后端地理编码大概率查不到或查出无关结果。这里在推荐这
一步识别出这类"类别名"，直接从数据集里按 type 挑出几个真实存在的候选
（content_recommender.recommend_by_type()，纯数据检索，不靠 LLM 编），
用跟 recommend_agent.recommend_grounded() **相同的字段形状**返回，好让
main.py 把两者拼进同一个 suggested_additions 列表——前端不用另起一块 UI，
用户在同一处推荐列表里勾选夜市和景点，两者是同一种"可选加入"的操作。

判断"笼统"的办法很朴素：跟一份手工列的词表做规整化后精确匹配（大小写/空格
不敏感），不是真的语义理解，也不识别"数据集里本来就有的具体夜市名字"（比如
用户如果直接说"Bugis Street Market"，那已经是具体地点，不会被这里拦截，
会走正常的抽取/地理编码流程）。够用是因为目标窄——只处理"提到类别但没指定
是哪一个"这一种场景，答辩如果被问"怎么判断地点具体不具体"要如实说这点，
不是真的自然语言消歧。

当前只接了"夜市/night market"这一个类别（对应 F-18 这次的具体需求），
数据集里对应的候选目前只有两条手工补的真实夜市（Chinatown Street Market /
Bugis Street Market，见 data/processed/singapore_attractions.csv 的
indoor_outdoor_source=manual 那两行——**不是**从 data.gov.sg 官方数据集来的，
是查了公开资料手工核对经纬度加进去的，跟 107 条官方数据集的其余部分来源不同，
如实说明）。以后要扩展别的笼统类别（比如"海滩""博物馆"）只需要在
VAGUE_PLACE_CATEGORIES 里加词条，不用改这个文件的逻辑。
"""

# 值是 content_recommender 数据集里对应的 type 列（决定候选从哪个类别选）。
# 中英文混着列是因为抽取阶段的模型可能直接照抄用户原话（含中文）进 place name，
# 也可能翻译成英文，两种都要接住。
VAGUE_PLACE_CATEGORIES: dict[str, str] = {
    "night market": "market",
    "night markets": "market",
    "nightmarket": "market",
    "night bazaar": "market",
    "pasar malam": "market",
    "bazaar": "market",
    "market": "market",
    "markets": "market",
    "夜市": "market",
    "夜市场": "market",
    "夜间市场": "market",
    "集市": "market",
    "市场": "market",
}


def _normalize(name: str) -> str:
    return " ".join(name.strip().lower().split())


def find_vague_places(places: list[dict]) -> list[dict]:
    """
    从已抽取/已确认的地点列表里找出笼统类别名。

    返回 [{"original_name": ..., "category": ...}, ...]，没有笼统地点时返回 []。
    """
    found = []
    for place in places:
        category = VAGUE_PLACE_CATEGORIES.get(_normalize(place.get("name", "")))
        if category:
            found.append({"original_name": place.get("name", ""), "category": category})
    return found


def resolve_vague_place_suggestions(places: list[dict], top_n: int = 3) -> list[dict]:
    """
    找出 places 里的笼统类别名，逐个换成数据集里的具体候选，返回的每条都跟
    recommend_agent.recommend_grounded() 的输出**同一个字段形状**
    (name/type/lat/lng/distance_km/similarity/reason/activities)，
    main.py 可以直接把结果拼进 suggested_additions 同一个列表，不用改
    /recommend 的响应结构。

    同一个 category 只查一次——places 里出现两条"夜市"不会查两遍、
    不会推荐出重复候选。
    """
    from content_recommender import recommend_by_type  # 延迟导入，避免模块间循环 import

    vague = find_vague_places(places)
    if not vague:
        return []

    seen_categories: set[str] = set()
    suggestions: list[dict] = []
    for item in vague:
        category = item["category"]
        if category in seen_categories:
            continue
        seen_categories.add(category)

        for c in recommend_by_type(category, user_places=places, top_n=top_n):
            suggestions.append(
                {
                    "name": c["name"],
                    "type": c["type"],
                    "lat": c["lat"],
                    "lng": c["lng"],
                    "distance_km": c["distance_km"],
                    # 没有 TF-IDF 相似度可算（类别匹配不是文本相似度），
                    # 用 None 而不是编一个假分数——跟 coords/dates 那条"宁可为空
                    # 也不编"的原则一致。
                    "similarity": None,
                    "reason": (
                        f'You mentioned "{item["original_name"]}" without naming a specific '
                        f"one, so here is a real {category} to choose from."
                    ),
                    "activities": [],
                }
            )
    return suggestions


if __name__ == "__main__":
    # 手动冒烟测试：不需要 Ollama/AWS，纯本地数据检索
    demo_places = [
        {"name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": []},
        {"name": "night market", "type": "market", "activities": []},
    ]
    for s in resolve_vague_place_suggestions(demo_places):
        print(f"{s['name']} ({s['distance_km']} km) -- {s['reason']}")
