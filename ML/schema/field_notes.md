# Trip Extraction JSON Schema - 字段设计笔记

来源：用 sample_1/2/3.txt 人工标注 + 对齐 `Documents/Shared Documents/DATA_DICTIONARY_zh.md` 里的 `destination` 表。

> **2026-08-03 更新**：`address`/`source` 两个字段已经删掉，这份 JSON 不再
> 要求跟后端 `destination` 表对齐——它现在是「抽取 Agent -> 推荐 Agent」内部
> 流转的中间结构，只有推荐 Agent（`ML/app/recommend_agent.py`）的输出才会传
> 给后端。详细决策见 `ML/docs/backend_alignment_brief.md` 问题 1。下表按当前
> 字段更新。

## 顶层字段

| 字段            | 类型     | 必填           | 说明                                                                                                                  |
| --------------- | -------- | -------------- | --------------------------------------------------------------------------------------------------------------------- |
| `destination` | string   | 是             | 整趟行程的目的地城市/地区，如 "Singapore"                                                                             |
| `dates`       | string[] | 否，默认`[]` | 文本里明确写出的日历日期（YYYY-MM-DD）。很多攻略只写 "Day 1/Day 2" 没有真实日期，这种情况留空数组，不要让模型瞎编年份 |
| `places`      | object[] | 是，至少 1 项  | 见下表                                                                                                                |

## `places[]` 里每个地点的字段

| 字段 | 类型 | 必填 | 对齐数据库 `destination` 表哪一列 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | `name` | 地点名称 |
| `type` | enum: attraction / restaurant / hotel / market / other | 是 | `category`（DB 是 varchar 不限枚举，但抽取阶段先用枚举保证一致性，方便后面任务 6 做 TF-IDF 时类别干净） | 地点类型 |
| `coords` | {lat, lng} \| null | 否，默认 `null` | `latitude` / `longitude`（DB 里是 NOT NULL！） | **禁止模型编造坐标**，缺失填 null；真实坐标由下游单独用地图 API 查，见下方"待讨论"部分 |
| `activities` | string[] | 否，默认 `[]` | 无对应列，可能存进 `trip_schedule.note` 或前端展示用 | 在这个地点做的事情 |

`address`、`source` 两个字段已删掉，理由和决策记录见 `ML/docs/backend_alignment_brief.md` 问题 1。

##  待和团队确认的事项

1. `latitude`/`longitude` 是 `NOT NULL`——AI 抽取阶段产出的 `coords` 可能是 `null`，说明入库前必须有一个"地理编码补全"步骤（用地名查坐标），不能指望 AI 直接给坐标。这个依赖关系要提前告诉负责建表/建库流程的同学。
2. `type` 枚举值现在是我按 3 篇样本文本反推出来的（attraction/restaurant/hotel/market/other），真实数据集变多之后大概率还要再调整，不用一次定死，但改动要同步给后端。
