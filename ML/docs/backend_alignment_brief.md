# 接后端对齐清单（会前准备，源自 `ML/schema/field_notes.md`）

> 给 Zhengchaorui / moyundi 看的对齐材料，3 个问题，每个都是"问题 + 现状 +
> 我这边建议的方案"，方便当面直接过一遍、当场定下来。

---

## 问题 1（已解决，2026-08-03）：`source` / `address` 两个字段，`destination` 表里没有对应列

**原来的现状**：AI 抽取出来的每个地点都会带 `source`（数据来自哪份输入文本/URL）
和 `address`（原文提到的地址，常常是 null），但对照
`Documents/Shared Documents/DATA_DICTIONARY_zh.md` 的 `destination` 表，这
两列都不存在。

**结论**：不落库，直接把这两个字段从抽取阶段的 JSON 里删掉了（见
`ML/schema/trip_models.py` / `trip_schema.json`）。理由是链路本身变了：
抽取出来的 JSON 现在只是「抽取 Agent -> 推荐/分析 Agent」内部流转的中间结构
（见下方"链路更新"），不要求跟后端表结构对齐；最终传给后端的是推荐 Agent
的输出（`RecommendationResult`，见 `ML/app/recommend_agent.py`），那份结果
里没有 per-place 的 `source`/`address` 字段的必要：
- `source`：一次请求（`ExtractTravelInfoRequest.source_url` /
  一次聊天会话）本来就只对应一份输入，没必要精确到每个地点
- `address`：原文里基本是 null，真要落库还是得靠后端 `MapPlacesClient` 地理
  编码反查，模型抽取阶段给不出真实地址，删掉更诚实

**链路更新**：产品路径按 `DATA_DICTIONARY_zh.md`（用户粗略路线 -> 与 AI
Agent 多轮完善）拆成三个本地部署的模型/agent，`ML/app/orchestrator.py`
串联：
1. `chat_filter.py`——先把 `chat_message` 历史里的寒暄/无关内容过滤掉
2. `local_llm_client.py`——本地 Ollama 模型抽取成 `TripExtraction`
3. `recommend_agent.py`——分析已抽取地点 + 偏好，推荐新的地点

三个阶段各自的模型可以独立配置（`FILTER_MODEL` / `EXTRACT_MODEL` /
`RECOMMEND_MODEL` 环境变量），不用共用同一个模型。

---

## 问题 2：`coords` 允许为 `null`，但 DB 的 `latitude`/`longitude` 是 `NOT NULL`

**现状**：Schema 明确禁止模型编造坐标，抽取阶段 `coords` 缺失就是 `null`；
但 `destination` 表的 `latitude`/`longitude` 不允许 `NULL`。

**要定的事**：谁在哪个环节把 `null` 坐标补成真实坐标（地理编码：拿地名去
查经纬度）？入库前必须有这一步，不能指望 AI 直接给坐标。

**我这边建议**：地理编码放在"抽取结果确认之后、正式入库之前"这一步做，
按地名查坐标（比如用 Google Maps Geocoding API），谁来实现这段代码需要
当面定——如果后端已经要接地图 API 做别的功能（比如 F-13 地图展示），顺路
一起做可能最省事。

---

## 问题 3：`type`（枚举）和 DB 的 `category`（自由文本）怎么对齐

**现状**：抽取阶段 `type` 用固定 5 个枚举值
（attraction/restaurant/hotel/market/other），是根据 3 篇 spike 样本手工
反推出来的，不保证覆盖所有情况（比如已经遇到"屋顶酒吧"这种不好归类的，
见 `ML/samples/ground_truth/labeling_notes.md` 第 3 条）；DB 的 `category`
列是不限值域的 varchar。

**要定的事**：入库时直接原样存枚举值，还是需要一层映射/扩展值域？

**我这边建议**：现阶段直接存枚举值，先跑通链路；等真实数据变多、枚举值
不够用的情况出现了，再一起商量要不要扩展或改成自由文本+枚举校验两级。

---

## 会后待办（视讨论结果补充）
- [ ] 问题 1 结论：`source` / `address` 加不加列
- [ ] 问题 2 结论：地理编码由谁实现、放在哪一步
- [ ] 问题 3 结论：`type`/`category` 映射方式
