# 标准答案标注笔记

`sample_1/2/3.json` 是按 `trip_schema.json` 手工标注的"标准答案"，供以后真跑
`compare_models.py`/`bedrock_client.py` 时直接用代码比对模型输出，不用当天现读
原文数漏抽/编造。已跑 `jsonschema` 校验，3 份全部 PASS。

标注时的判断取舍，写下来是为了让人（包括自己）之后能复查，不是随口编的：

## 1. 三篇的 `dates` 全部标成 `[]`

`sample_1.txt` 写了 "Day 1 (Aug 12)" 这种月+日，但**没有年份**。
`trip_schema.json` 和 `field_notes.md` 都明确写了"不要让模型瞎编年份"——
既然真实年份文本里没给，标准答案也不该编一个年份进去，所以按规则留空数组。

**这个发现顺带说明一件事**：`mock_client.py` 里 `mock_extract()` 写死返回
`"dates": ["2025-08-12", "2025-08-13"]`，年份 2025 是编的（凑巧月日和
sample_1 对上）。这个 mock 数据本身不完全符合"不许编年份"的规则，但它只是
占位数据，不影响 main.py 的逻辑，先记录在这里，不用现在改。

## 2. 没写名字的地点，不纳入 `places`

- `sample_2.txt`："checked into a hotel near Clarke Quay"（酒店没具体名字）
- `sample_2.txt`："relaxed with a foot massage near the hotel"（按摩店没具体名字）

`name` 是必填字段，原文没给具体名字就不能编一个，所以这两处直接不算作
`places` 里的条目，不是漏标。

## 3. `type` 枚举里没有完全对应的类型，靠人工判断

- `1-Altitude`（sample_2，屋顶酒吧看夜景）：枚举只有
  attraction/restaurant/hotel/market/other，没有"bar"。按"主要是去喝东西/
  看风景"判断更接近 other 而不是 restaurant，标成了 `other`。这是主观判断，
  如果后端/老师有不同意见，这条最可能被挑战，先记在这里方便讨论。

## 4. 地标/参照物不算独立地点

- `sample_1.txt` 的 "Sentosa Island" 只是行程去 Universal Studios/Wings of
  Time 路上的地理位置说明，不单独算一个 place。
- `sample_3.txt` 的 "Sultan Mosque" 只是用来说明 Zam Zam Restaurant 的位置
  （"near Sultan Mosque"），本身没被描述成"去过/做了什么"，不单独算一个 place。

## 5. `coords` 全部是 `null`

三篇原文都没有给出具体经纬度，符合 schema 里"没有就填 null，不要编造"的要求，
没有例外。

## 6. 2026-08-03 更新：`address`/`source` 字段已从 schema 里删掉

这份抽取 JSON 现在是「抽取 Agent -> 推荐/分析 Agent」内部流转的中间结构，不
再需要跟后端 `destination` 表对齐，所以把 `address`（原文基本没有门牌地址）
和 `source`（一次请求本来就带 source_name，放在每个 place 上是冗余）都删了。
`sample_1/2/3.json` 已同步去掉这两个字段，`compare_models.py` 里原来给
`source` 打补丁再校验的那步也一并删掉了。
