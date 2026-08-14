"""
Task 1 的 Pydantic 版本。

跟 trip_schema.json 是同一份字段设计（依据见 field_notes.md），只是换成用
Python 类来写，而不是手写 JSON Schema 语法。好处：
1. 校验自带，不用单独装 jsonschema 库：TripExtraction.model_validate(data)
2. 能直接喂给 LangChain 的 with_structured_output()，不用手动转换格式
3. 只有一份"真相来源"，以后改字段只改这一个文件，JSON 版可以用
   trip_schema_from_pydantic() 自动生成，不用手动同步两份文件

字段设计原则（见 field_notes.md 更新说明）：这份 JSON 是「抽取 Agent -> 推荐/分析
Agent」内部流转用的中间结构，不要求跟后端 `destination`/`draft_place` 表的列一一对应。
所以去掉了两个曾经想拿去落库的字段：
- `address`：原文很少写门牌地址，真要落库靠下游地图 API 反查，不需要模型抽取阶段猜
- `source`：整条请求本来就带 source_name/source_url（一次请求一份输入文本，不需要
  精确到每个地点），放在 place 级别是冗余信息
"""
from typing import Annotated, Literal, Optional

from pydantic import BaseModel, Field

# 每条 activity 文字的长度上限。注意必须用 Annotated 套在元素类型上：
# 直接写 activities: list[str] = Field(max_length=255) 限制的是"列表最多几个元素"，
# 不是"每条文字最多几个字"，那是另一回事。
ActivityText = Annotated[str, Field(max_length=255)]


class Coords(BaseModel):
    lat: float
    lng: float


class Place(BaseModel):
    # max_length 跟后端 DB 对齐：draft_place.name 是 VARCHAR(255)。模型偶尔会把
    # 一整句话当成地点名输出，不设上限的话这种结果能通过校验，却在后端落库时才炸，
    # 排查起来要跨两个服务。宁可在这里就判定不合规，让 extract_with_retry 重试。
    # description 一律写英文：这些描述会被 model_json_schema() 序列化进 system prompt
    # 直接喂给模型，prompt 里混中文会诱导模型往中文输出上偏（实测本地 8B 模型对中文
    # 输入会吐乱码地名）。给人看的解释放注释里，注释不会进 prompt。
    name: str = Field(max_length=255, description="Place name only, never a whole sentence")
    type: Literal["attraction", "restaurant", "hotel", "market", "other"] = Field(
        description="Place category, must be exactly one of the five enum values"
    )
    coords: Optional[Coords] = Field(
        default=None,
        description="Latitude and longitude. Never invent coordinates: always return null. "
        "Real coordinates are filled in later by a geocoding step.",
    )
    # 同理对齐 draft_activity.title VARCHAR(255)：一个 activity 落库一行，所以逐条限长
    activities: list[ActivityText] = Field(
        default_factory=list, description="What the traveller does at this place"
    )


class TripExtraction(BaseModel):
    destination: str = Field(description="The city or region of the whole trip, e.g. 'Singapore'")
    dates: list[str] = Field(
        default_factory=list,
        description="Calendar dates explicitly stated in the text, format YYYY-MM-DD. "
        "If the text only says Day 1 / Day 2 with no real dates, return an empty array. "
        "Never invent a year.",
    )
    # 为什么单独加这个字段，而不是让下游从 dates 推算：dates 是"文中明确写出的日历
    # 日期"，["2026-08-09", "2026-08-11"] 到底是"9号到11号玩3天"还是"9号和11号各去
    # 一次"，从数组本身分辨不出来。而 /plan-itinerary 的 num_days 必须是个确定的数，
    # 猜错了整个行程的天数就是错的。所以让模型直接抽"文本说玩几天"这件事本身，
    # 抽不到就是 None —— 跟 coords/dates 一样，宁可为空也不编。
    # 上限 30 对齐 itinerary_planner.MAX_DAYS，免得抽出个 200 天传下去才被拒。
    duration_days: Optional[int] = Field(
        default=None,
        ge=1,
        le=30,
        description="Total number of days the whole trip lasts, if the text states it "
        "(e.g. 'a 3-day trip', or the text is organised as Day 1 / Day 2 / Day 3). "
        "Never guess or infer this from the number of places: return null when the "
        "text does not say how long the trip is.",
    )
    places: list[Place] = Field(min_length=1, description="Every place mentioned in the text")


if __name__ == "__main__":
    # 证明"库真的能自动生成等价的 schema"，而不是空口说白话
    import json

    print(json.dumps(TripExtraction.model_json_schema(), ensure_ascii=False, indent=2))
