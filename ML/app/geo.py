"""
地理距离计算（F-18 "nearby" 那一半需要的工具）。

为什么自己算而不调 Google：后端 `RoutingClient`（F-14）已经计划接 Google
Routes API 拿真实车程，AI 服务这边再配一套密钥、往同一个账号计费很乱，
分工上说好了 Google 由后端调（见 `ML/docs/ai_contract.md` 第 3 节）。
所以这里只做纯数学的直线距离：不联网、不要密钥、没有配额、毫秒级返回，
先把"附近"这个能力跑通，等后端把真实车程通过 `travel_matrix` 传进来再升级。

直线距离的局限（答辩被问就如实说）：不考虑跨海、单行道、地铁线路走向，
所以圣淘沙这种要过跨海大桥的地方，直线距离会比实际路程乐观。新加坡整体
只有 50km 宽，同一片区域内的误差可以接受，用来做"排除明显跑远了的地点"够用。
"""
import math

# 地球平均半径（km）。用球面近似而不是椭球体：新加坡这个尺度下两者差异
# 远小于"直线距离 vs 实际路程"本身的误差，没必要引入更复杂的公式。
EARTH_RADIUS_KM = 6371.0


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """
    两个经纬度之间的球面直线距离（km），用 haversine 公式。

    不用简单的勾股定理，是因为经度 1 度对应的实际距离随纬度变化（赤道最宽、
    两极收缩为 0），直接拿经纬度差做平方和在高纬度会严重偏大。haversine 走
    球面三角，任何纬度都成立。
    """
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lng2 - lng1)

    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def min_distance_to_places(lat: float, lng: float, places: list[dict]) -> float | None:
    """
    候选地点到"用户已确认的一组地点"里最近那个的距离（km）。

    取最小值而不是平均值：用户如果同时安排了乌节路和樟宜机场（相隔很远），
    平均距离会把两头的候选都算成"中等远"，反而把真正贴着其中一个的地点排下去。
    最小值回答的是"这个推荐至少挨着用户的某一个行程点吗"，这才是"nearby"的本意。

    places: [{"lat": ..., "lng": ...}, ...]，缺坐标的条目直接跳过。
    返回 None 表示一个能用的坐标都没有（比如后端还没做地理编码），
    调用方据此退回纯文本相似度排序，而不是当成"距离为 0"。
    """
    distances = [
        haversine_km(lat, lng, p["lat"], p["lng"])
        for p in places
        if p.get("lat") is not None and p.get("lng") is not None
    ]
    return min(distances) if distances else None


if __name__ == "__main__":
    # 手动冒烟测试：不需要 Ollama/AWS/网络，纯本地算
    gardens = (1.2816, 103.8636)  # 滨海湾花园
    museum = (1.2966, 103.8485)  # 国家博物馆
    sentosa = (1.2494, 103.8303)  # 圣淘沙

    # 输出一律英文：Windows 终端默认 GBK，打中文会变乱码看不清
    print(f"Gardens by the Bay -> National Museum: {haversine_km(*gardens, *museum):.2f} km")
    print(f"Gardens by the Bay -> Sentosa:         {haversine_km(*gardens, *sentosa):.2f} km")
    print(f"Same point should be 0:                {haversine_km(*gardens, *gardens):.2f} km")

    user_places = [{"lat": gardens[0], "lng": gardens[1]}, {"lat": museum[0], "lng": museum[1]}]
    print(f"Sentosa -> nearest planned stop:       {min_distance_to_places(*sentosa, user_places):.2f} km")
    print(f"When user places have no coords:       {min_distance_to_places(*sentosa, [{'lat': None, 'lng': None}])}")
