# Trip Extraction JSON Schema - 字段设计笔记

来源：用 sample_1/2/3.txt 人工标注 + 对齐 `Documents/Shared Documents/DATA_DICTIONARY_zh.md` 里的 `destination` 表。

## 顶层字段

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `destination` | string | 是 | 整趟行程的目的地城市/地区，如 "Singapore" |
| `dates` | string[] | 否，默认 `[]` | 文本里明确写出的日历日期（YYYY-MM-DD）。很多攻略只写 "Day 1/Day 2" 没有真实日期，这种情况留空数组，不要让模型瞎编年份 |
| `places` | object[] | 是，至少 1 项 | 见下表 |

## `places[]` 里每个地点的字段

| 字段 | 类型 | 必填 | 对齐数据库 `destination` 表哪一列 | 说明 |
|---|---|---|---|---|
| `name` | string | 是 | `name` | 地点名称 |
| `type` | enum: attraction / restaurant / hotel / market / other | 是 | `category`（DB 是 varchar 不限枚举，但抽取阶段先用枚举保证一致性，方便后面任务6做TF-IDF时类别干净） | 地点类型 |
| `address` | string \| null | 否，默认 `null` | 无直接对应列，需跟后端确认要不要加 | 文本常常没有地址，缺失就填 null，不要编 |
| `coords` | {lat, lng} \| null | 否，默认 `null` | `latitude` / `longitude`（DB 里是 NOT NULL！） | **禁止模型编造坐标**，缺失填 null；真实坐标由下游单独用地图 API 查，见下方"待讨论"部分 |
| `activities` | string[] | 否，默认 `[]` | 无对应列，可能存进 `trip_schedule.note` 或前端展示用 | 在这个地点做的事情 |
| `source` | string | 是 | 无对应列，需跟后端确认 | **不由模型生成**，抽取完成后由我们的代码填入输入文本的文件名/URL |

## 待和团队确认的事项

1. `destination` 表目前没有 `source`、`address` 列——如果这两个字段要真正落库，需要找负责后端/F-04 建表的同学商量是否加列。
2. `latitude`/`longitude` 是 `NOT NULL`——AI 抽取阶段产出的 `coords` 可能是 `null`，说明入库前必须有一个"地理编码补全"步骤（用地名查坐标），不能指望 AI 直接给坐标。这个依赖关系要提前告诉负责建表/建库流程的同学。
3. `type` 枚举值现在是我按 3 篇样本文本反推出来的（attraction/restaurant/hotel/market/other），真实数据集变多之后大概率还要再调整，不用一次定死，但改动要同步给后端。
