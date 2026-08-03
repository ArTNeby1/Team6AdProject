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
from typing import Literal, Optional

from pydantic import BaseModel, Field


class Coords(BaseModel):
    lat: float
    lng: float


class Place(BaseModel):
    name: str = Field(description="地点名称")
    type: Literal["attraction", "restaurant", "hotel", "market", "other"] = Field(
        description="地点类型"
    )
    coords: Optional[Coords] = Field(
        default=None,
        description="经纬度。文本里基本不会出现坐标，禁止模型编造，缺失填 null，"
        "真实坐标由下游地理编码步骤补全",
    )
    activities: list[str] = Field(default_factory=list, description="在这个地点做的事情")


class TripExtraction(BaseModel):
    destination: str = Field(description="整趟行程的目的地城市/地区，例如 'Singapore'")
    dates: list[str] = Field(
        default_factory=list,
        description="文本中明确出现的日历日期，格式 YYYY-MM-DD。只写 Day 1/Day 2 "
        "没给真实日期时，留空数组",
    )
    places: list[Place] = Field(min_length=1, description="文本中提到的所有地点")


if __name__ == "__main__":
    # 证明"库真的能自动生成等价的 schema"，而不是空口说白话
    import json

    print(json.dumps(TripExtraction.model_json_schema(), ensure_ascii=False, indent=2))
