# AI 服务设计与接口说明 —— 前端 / 后端 / AI 三方对齐用

这份文件是这条流程的**唯一参考**：接口叫什么、传什么、返回什么、谁负责哪一段。
三个人照这份文件各自动手写代码，不用再互相猜格式。

> **状态（2026-08-08 更新）**：**AI 服务这一侧的接口全部实现完毕、测试通过**
> —— 解析、天气、按天气/距离排顺序、推荐、**按天拆分行程（F-09，第 5.5 节新增）**
> 都能真实跑起来，不是设计稿。
>
> 现在整条链路卡在后端：AI 抽出来的地点**还没有任何代码把它写进数据库**，
> 所以前端页面拿不到数据。要做什么见第 6 节「后端要做的」，🔴 那四条是硬阻塞。
>
> 后端本机不用装 Ollama 也能联调 —— 见第 4 节的 **mock 模式**。
>
> **⭐ 2026-08-13 更新（后端前端都要看）**：补上了 F-09 最后一块缺的拼图 ——
> `/extract-travel-info` 和 `/refine` 的返回体新增 **`duration_days`**（整趟玩几天，
> 文本没说就是 `null`）。在这之前抽取结果里没有任何"天数"信息，后端**无论怎么写
> 都造不出 `/plan-itinerary` 要的 `num_days`，F-09 是条断头路。现在这条路通了，
> 具体怎么用见第 5.5 节的「`num_days` 从哪来」。AI 侧到此 F-09 全部就绪，
> 剩下的纯粹是后端把 `AiPlanningClientHttp.generateDailyItinerary()` 那个 STUB
> 换成真调用 + 加一个 Controller 端点（第 6 节第 8 条）。
>
> 同一天核对出的另一件事，**不是 AI 侧能修的**：F-32 顺序优化（最近邻 + 2-opt）
> 现在在线上**完全没有生效**。原因是 `MapPlacesClientStub` 永远返回空，
> `DraftPlace` 的 lat/lng 一直是 `null`，传到 `/recommend` 的地点全都没坐标，
> 而没坐标就没法算距离 —— `_order_by_proximity()` 里 2-opt 那段根本进不去
> （这是有意的降级，见第 5 节降级行为表，不是 bug）。**后端把地理编码接上，
> F-32 和 F-18 的 nearby 部分立刻就有效果，AI 侧一行都不用改。**

> **⭐ 2026-08-14 更新（前端后端都要看，有一处不兼容改动）**
>
> 做的是"没说玩几天就问用户"这条产品逻辑的 AI 侧部分。三件事：
>
> 1. **新增 `needs_duration_input`（布尔，恒定存在）**，`/extract-travel-info` 和
>    `/refine` 的返回体里。`true` = 文本没说天数，**前端弹窗问用户**；`false` = 天数
>    已经有了，直接排。等价于 `duration_days == null`，但把"该怎么办"写死在 AI 侧，
>    省得三端各自解释一个 null（见第 4 节字段表）。
> 2. **🔴 不兼容改动：`/plan-itinerary` 的 `num_days` 变成必填**，原来漏传会默认
>    按 1 天排并返回 200。现在漏传返回 400，`detail` 以 `NUM_DAYS_REQUIRED:` 开头。
>    **后端 `AiPlanningClientHttp.planItinerary()` 一直是显式传值的，所以实际调用
>    行为不变**，这条只挡"忘了接"的情况。
> 3. `chat_filter` 的降噪 prompt 加了"trip length 经常是一条很短的独立消息（光一个
>    `3 days` 甚至光一个 `3`），必须原样保留" —— 弹窗答案如果是走 `/refine` 回来的，
>    以前会被当寒暄过滤掉。
>
> 另外 **mock 模式的 `duration_days` 现在看输入文本**（以前恒定返回 2，等于弹窗那条
> 分支在 mock 下永远测不到）：输入里有 "3 days"/"3天"/"Day 1..Day 3" 就返回对应天数，
> 没有就返回 `null`。前后端不装模型就能把两条分支都联调完，见第 4 节 mock 模式。
>
> 自测脚本：`python ML/app/test_duration_flow.py`（不需要 AWS/Ollama，16 条全绿）。
>
> ⚠️ 上面 2026-08-13 那条里"后端 `generateDailyItinerary()` 还是 STUB"已经过时了 ——
> 后端在 `origin/main` 上已经接好了真调用（`planItinerary()` + `POST /trips/{id}/generate`）。
> 但**前端还没有任何地方调那个端点**，且 `confirmSession()` 仍写死 `setDurationDays(1)`，
> 这两条是这个功能端到端跑通剩下的缺口，都在 AI 侧之外。

> **S3 进度（2026-08-08）**：接口层没变化（后端联调还是卡点），但补齐了 S2/S3
> 的几项欠账，都是独立于后端进度、可以先做的：F-18 补了 grounding 逻辑的自动化
> 测试、F-32 顺序优化加了 2-opt、F-33 人流预测原型（季节性代理信号，还没接
> 生产链路）、把 S0 的人工模型评估形式化成了脚本。完整数字见
> `docs/model_evaluation.md`。

---

## 1. 整体流程

组长给的方向：

> 输入："我今天要去iss学习然后去圣淘沙游泳"
>
> 1. 本地 LLM 解析出：地点 = iss、圣淘沙，活动 = 学习、游泳
> 2. 输出 JSON 给前端展示
> 3. 用户确认（或修改）后点确认
> 4. 确认后的 JSON 传给 agent
> 5. agent 生成建议，存入数据库

拆成两个接口 + 中间一个后端步骤：

```
用户输入文字
    │
    ▼
┌─────────────────────────────┐
│ 接口一  POST /extract  ✅   │  解析出地点 + 活动
└─────────────────────────────┘
    │
    ▼  前端展示，用户确认 / 编辑
    │
    ▼  ⚠️ 后端在这里补经纬度（MapPlacesClient 地理编码）
    │
┌─────────────────────────────┐
│ 接口二  POST /recommend  ✅ │  agent：查天气 + 算距离 + 排顺序 + 推荐（单天）
└─────────────────────────────┘
    │
    ▼  后端存进数据库

    行程超过一天时，另外调：
┌──────────────────────────────────┐
│ 接口三  POST /plan-itinerary  ✅ │  按天拆分（F-09），见第 5.5 节
└──────────────────────────────────┘
```

接口二和接口三**都要求地点已经有经纬度**，所以后端地理编码那一步对两者都是前提。
两个接口互相独立，可以只调其中一个：单天行程调接口二就够了，多天行程调接口三
（要推荐再补调接口二）。

**中间那一步是硬性依赖**：接口二要按距离排顺序，所以调它之前地点必须已经有
`lat`/`lng`。接口一返回的 `coords` 是 `null`（模型不许编坐标），后端必须先地理编码。

---

## 2. ⚠️ 两层接口 —— 前端不直接调 AI 服务

这一点很容易搞混，先说清楚：

```
前端  ──HTTP──▶  Java 后端 :8080  ──HTTP──▶  Python AI 服务 :8001
      第一层                        第二层
```

**前端永远不直接调 Python 服务。** 原因：

1. AI 服务在内网/本机，浏览器根本连不上
2. 会话状态、数据库、用户鉴权都在 Java 那边
3. 以后要切 Bedrock 还是本地模型，前端完全不用知道

所以：

- **前端要照着的是「第一层」的 Java 接口**（下面 2.1）
- **后端要照着的是「第二层」的 Python 接口**（第 4、5 节）
- 本文件第 4、5 节的 JSON 格式，前端不会直接收到，但**字段内容会被后端原样透传**，
  所以前端设计界面时看那边的字段是准的

### 2.1 第一层：前端 ↔ Java 后端（解析 + 确认这一段）

下面是**提议**的接口设计。Java 那边的路径、字段名最终由后端定，
这里列的是"前端完成这个流程至少需要哪些接口"，请后端确认或调整。

| 步骤            | 方法   | 路径                                                             | 后端内部做什么                                                                              |
| --------------- | ------ | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| ① 用户输入文字 | POST   | `/api/v1/planning-sessions`                                    | 建会话 → 调 Python`/extract-travel-info` → 地理编码 → 存 `draft_place` → 返回给前端 |
| ② 用户改地点名 | PUT    | `/api/v1/planning-sessions/draft-places/{placeId}`             | 直接改数据库，**不重新调 LLM**                                                        |
| ③ 用户删地点   | DELETE | `/api/v1/planning-sessions/draft-places/{placeId}`             | 直接删，**不重新调 LLM**                                                              |
| ④ 用户补充说明 | POST   | `/api/v1/planning-sessions/{sessionId}/messages` + `/refine` | 调 Python`/refine` 重新解析                                                               |
| ⑤ 用户点确认   | POST   | `/api/v1/planning-sessions/{sessionId}/confirm`                | 调 Python`/recommend` → 存结果 → 返回                                                   |

**②③④ 已经存在**（`PlanningController.java` 里有），①⑤ 目前是 501 占位。

### 2.2 编辑时要不要重新解析？—— 不要

这是个容易踩坑的设计点，定死规则：

| 用户行为                 | 处理方式                         | 为什么                                                                       |
| ------------------------ | -------------------------------- | ---------------------------------------------------------------------------- |
| 改名字 / 删地点 / 改活动 | **直接改数据库，不调 LLM** | 用户是在**纠正** LLM 的错误，再跑一次 LLM 很可能把用户的修改又覆盖回去 |
| 又打了一段新文字         | 调`/refine` 重新解析           | 这是新信息，需要 LLM 理解                                                    |

### 2.3 前端在「确认界面」拿到的数据

后端 ① 返回给前端的，应该是补完经纬度之后的地点列表。字段来自 Python 接口一（第 4 节），
外加后端补的 `place_id` 和真实坐标：

```json
{
  "session_id": 17,
  "places": [
    { "place_id": 101, "name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["take photos"] },
    { "place_id": 102, "name": "National Museum of Singapore", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["see the exhibition"] }
  ]
}
```

⚠️ 跟 Python 直接返回的差别：**多了 `place_id`**（编辑/删除要用它定位）、
**`coords: null` 变成了真实的 `lat`/`lng`**（后端地理编码补的）。

### 2.4 前端在「结果界面」拿到的数据

后端 ⑤ 返回的，就是 Python 接口二（第 5 节）的返回体。
返回三块内容，**都已实现**：

| key | 是什么 | 前端怎么用 |
|---|---|---|
| `weather_summary` | 当天天气一句话总结 | 结果页顶部提示，例如 "Rain expected in the afternoon" |
| `ordered_stops` | **用户自己确认的**地点，被 agent 按天气+距离重排了顺序 | 主行程列表，按 `order` 排，可按 `time_of_day` 分上午/下午/晚上 |
| `suggested_additions` | **AI 额外推荐的**新地点 | 单独一块"推荐加入" |

⚠️ 注意 `weather_summary` 和 `time_of_day` 在没传日期或天气查不到时会是 `null`，
前端要能处理这种情况（详见第 5 节的降级行为表）。
后端可能会额外加 `place_id` 之类的字段方便前端操作，具体由后端定。

---

## 3. Agent 设计 —— 它到底做什么

组长只说了"agent 生成建议"，这里把内部定清楚。

**先说明**：这里的 "agent" 指"一个会调用工具、自己决定怎么做的服务"，
**不是** AWS Bedrock Agents 那个具体产品（控制台配 Action Group + Lambda 那一整套）。
项目没有要求必须用那个服务，用它要多开好几个 AWS 资源，这次不需要。
模型本身还是用任务 2 选定的 Bedrock Nova Lite（见 `model_selection.md`）或本地 Ollama。

### Agent 的工具

| 工具              | 做什么                                 | 数据来源                                    | 状态                                |
| ----------------- | -------------------------------------- | ------------------------------------------- | ----------------------------------- |
| `search_places` | 从 107 个真实新加坡景点里找相似的      | `singapore_attractions.csv`               | ✅ `content_recommender.py` |
| `get_distance`  | 算两个地点之间的直线距离               | 经纬度做 haversine 计算，纯数学不调 API     | ✅ `geo.py`（2026-08-08 完成） |
| `group_by_area` | 把邻近的地点归到一组，避免来回横跨全岛 | 同上                                        | ✅ `itinerary_planner.py` 的 `_split_into_days()`（2026-08-08 补做，见下） |
| `get_weather`   | 查当天/未来时段的天气预报              | data.gov.sg 的 NEA 天气接口（免费、免密钥） | ✅ `weather.py`（2026-08-08 完成） |
| `plan_stops`    | 按天气+距离把地点排出顺序              | 上面三个工具的结果                          | ✅ `itinerary_planner.py` |

**`group_by_area` 的来龙去脉**：一开始**没单独做** —— 做推荐时发现用距离直接参与打分
就够了（`content_recommender.py` 的 `hybrid` 模式把相似度乘上一个就近系数，距离 5km
打对折），效果上等于"优先推荐同一片区域的地点"，不用再单独聚类。当时记了一句
"真要做按天分组行程时可能还得补回来"。**2026-08-08 做 F-09 时确实补回来了** ——
按天分行程绕不开"哪些地点算同一片"，实现在 `_split_into_days()`（先串线路再切段，
不是聚类，理由见第 5.5 节）。

**一次性准备工作（不是运行时工具）**：✅ **已完成** —— 107 个地点都打上了"室内/室外"标记，
存在 CSV 的 `indoor_outdoor` 列（56 室外 / 51 室内）。脚本是 `ML/scripts/label_indoor_outdoor.py`，
先用关键词规则判（覆盖 85/107 = 79%），剩下 22 条交给 LLM。

**没有这个标记，天气数据是废的** —— 知道下午下雨但不知道哪些地方是室内的，agent 没法据此做决定。

⚠️ **准确度要如实说**：这是启发式分类，不是人工核对过的。规则那部分可靠（Museum→室内、
Park→室外这种通名很稳），LLM 那 22 条会有错（比如把"小印度"这种街区判成室内）。
答辩被问就说"79% 靠规则，剩下靠模型，没有人工逐条核对，存在误分类"。

### Agent 能做的五件事

1. **按天气排顺序** —— 下午有雨 → 户外景点排上午，室内的排下午
2. **按地理位置排顺序** —— 邻近的排一起，不要圣淘沙 → 樟宜 → 圣淘沙来回跑
3. **补充推荐** —— 用户只说了 2 个地点，一天还有空，从数据集里推荐附近的
4. **按天拆分行程（F-09）** —— 多天的行程，把同一片区域的地点排进同一天
5. **提示冲突** —— "这两个地点相隔 45 分钟车程，一个下午安排得比较紧" ⬜ 还没做

### 关于距离：跟后端的分工

后端的 `RoutingClient`（F-14）计划接 Google Routes/Directions API，返回结构里
已经有 `durationMinutes`（时长）和 `distanceKm`（距离）两个字段，
Google 的 transit 模式还能给真实地铁/公交时间。

**分工约定**：

- **Google API 由后端调，AI 服务不调** —— 两个服务各配一套密钥、都往同一个账号计费，很乱。
- AI 服务自己用 haversine 算直线距离，**先不依赖后端**，保证我这边不被卡住。
- 接口二的请求体里留一个**可选**字段 `travel_matrix`，后端 Google 那边做好之后填进来，
  agent 有就用真实车程、没有就退回直线距离。**这样后端做与不做，接口格式都不用改。**

---

## 4. 第二层接口一：解析 ✅ 已经能用（后端调，前端不直接调）

**代码**：`ML/app/main.py`

- `POST /extract-travel-info` —— 用户一次性说完（对应组长例子）
- `POST /refine` —— 多轮聊天（先过 `chat_filter` 去掉寒暄废话，再解析）

**服务地址**：本地开发 `http://localhost:8001`

> ### 🔧 后端联调用 mock 模式（不用装 Ollama）
>
> 后端本机大概率没装 Ollama（要拉几 GB 模型、CPU 跑一次几十秒）。只想把 HTTP
> 那一层调通的话，加三个环境变量起服务就行：
>
> ```powershell
> # Windows PowerShell
> $env:EXTRACT_PROVIDER="mock"; $env:FILTER_PROVIDER="mock"; $env:RECOMMEND_PROVIDER="mock"
> uvicorn main:app --reload --app-dir ML/app --port 8001
> ```
>
> mock 模式下 **JSON 结构跟真实模式一模一样**，字段一个不少，可以照着写解析代码；
> 而且是秒回（0.01 秒 vs 真实模型几十秒）。
>
> `/recommend` 返回的地点、坐标、距离**还是真的**（来自 107 条真实数据集，
> 纯 TF-IDF 算的不需要模型），只有推荐理由 `reason` 换成 `[MOCK]` 开头的模板句。
> `/extract-travel-info` 返回的**地点**是固定的两条假数据，不看输入内容 ——
> **但 `duration_days` 会真的看输入文本**（2026-08-14 改的），这样"没说天数就弹窗
> 问用户"那条分支在 mock 下也测得到：
>
> | 你传的 `raw_content` | `duration_days` | `needs_duration_input` |
> |---|---|---|
> | `"a 3-day trip in Singapore"` | `3` | `false` |
> | `"我想去新加坡玩5天"` | `5` | `false` |
> | `"Day 1: Gardens. Day 2: Sentosa."` | `2` | `false` |
> | `"I want to see Gardens by the Bay"` | `null` | **`true`** |
>
> 调通之后把这三个环境变量去掉就切回真实模型。

```
uvicorn main:app --reload --app-dir ML/app --port 8001
```

（`http://localhost:8001/docs` 有 FastAPI 自动生成的交互式测试页）

### 请求

```json
{ "raw_content": "I want to take photos at Gardens by the Bay, then visit the National Museum of Singapore to see the exhibition.", "source_url": null }
```

`/refine` 的请求换成聊天记录：

```json
{
  "messages": [
    { "role": "user", "content": "hey there" },
    { "role": "user", "content": "I want to take photos at Gardens by the Bay, then visit the National Museum to see the exhibition." }
  ],
  "preference_text": null
}
```

### 返回

```json
{
  "status": "OK",
  "destination": "Singapore",
  "dates": [],
  "duration_days": null,
  "needs_duration_input": true,
  "places": [
    { "name": "Gardens by the Bay", "type": "attraction", "coords": null, "activities": ["take photos"] },
    { "name": "National Museum of Singapore", "type": "attraction", "coords": null, "activities": ["see the exhibition"] }
  ]
}
```

**这就是前端做确认界面要用的 JSON。** 每个地点自带自己的活动列表（`Gardens by the Bay` 配 `take photos`、
`National Museum` 配 `see the exhibition`），不是两个分开的列表让前端自己配对。

### 字段说明

| 字段            | 说明                                                                                                                                      |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `destination` | **前端不用展示**。它的作用是范围校验：这个 app 只做新加坡，如果用户说"我想去曼谷"，这里会是 `"Bangkok"`，后端据此拒绝并提示用户。 |
| `dates`       | 文本里明确写了日期才有，没写就是空数组`[]` —— 不让模型编年份。                                                                        |
| `duration_days` | **⭐ 2026-08-13 新增，后端做 F-09 要用这个**。整趟行程一共几天，取值 1~30。文本明确说了才有（"a 3-day trip"、或正文本身是 Day 1 / Day 2 / Day 3 这种结构），**没说就是 `null`，不让模型猜**。这是 `/plan-itinerary` 的 `num_days` 的**唯一来源** —— 只有抽取这一步看得到原始文本，后端拿到的是结构化 JSON，这里不给就永远造不出来。⚠️ 不要自己从 `dates` 反推：`["2026-08-09","2026-08-11"]` 到底是"9号到11号玩3天"还是"9号和11号各去一次"，从数组本身分辨不出来。 |
| `needs_duration_input` | **⭐ 2026-08-14 新增，前端弹窗就看这个字段**。布尔值，**恒定存在**（不是"缺天数时才有"，可以无脑读）。`true` = 文本里没说玩几天，**必须先弹窗问用户**，拿到答案再排行程；`false` = 天数已经有了，直接用 `duration_days` 当 `num_days`。它恒等于 `duration_days == null`，单独给一个字段是为了把"这时候该怎么办"这条规则定死在 AI 侧，省得三端各自解释一个 null。 |
| `coords`      | **永远是 `null`**，是故意的。模型禁止编坐标，真实经纬度由后端 `MapPlacesClient` 查地图 API 补上。                               |
| `type`        | 枚举：`attraction` / `restaurant` / `hotel` / `market` / `other`                                                                |

---

## 5. 第二层接口二：推荐 ✅ 已实现（2026-08-08）

**`POST /recommend`**

实现方式（F-18）：先用 `content_recommender.py` 从 107 个真实新加坡景点里检索出
候选（TF-IDF 相似度 + 距离），再让 LLM 从候选名单里挑选并写推荐理由。
模型只负责"选哪个、为什么"，不负责"有哪些地方"，所以不会推荐出不存在的景点 ——
模型如果输出了候选名单之外的名字，代码会直接丢弃那一条。

`mode` 参数对应 F-18 标题的 "nearby or similar"：
`similar`（只看文字相似）/ `nearby`（只看距离）/ `hybrid`（两者结合，默认）。

**什么时候调**：用户确认地点之后、**且后端已经补好经纬度之后**。

### 请求

```json
{
  "date": "2026-08-07",
  "places": [
    { "name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["take photos"] },
    { "name": "National Museum of Singapore", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["see the exhibition"] }
  ],
  "travel_matrix": null,
  "preference_text": null
}
```

| 字段                | 必填 | 说明                                                        |
| ------------------- | ---- | ----------------------------------------------------------- |
| `places`          | 是   | 用户确认后的地点。带 `lat`/`lng` 才能算距离，没有会自动退回纯文本相似度（不报错） |
| `date`            | 否 | 查天气用。**传了才有天气排序**（户外躲雨），不传就只按距离排，`weather_summary` 和 `time_of_day` 会是 `null` |
| `mode`            | 否 | `similar` / `nearby` / `hybrid`（默认）。对应 F-18 的 "nearby or similar" |
| `top_n`           | 否 | 推荐几条，默认 3                                            |
| `max_distance_km` | 否 | 传了就排除超过这个距离的候选                                 |
| `preference_text` | 否 | 用户偏好，比如`"travel_style=culture"`                    |
| `travel_matrix`   | 否 | 后端 Google API 做好之后填这里（见下），现在传`null` 就行 |

**关于 `date` 改成选填**（前端反馈：用户说话里经常没有日期）：

用户输入 "I want to visit Gardens by the Bay" 是没有日期的，硬要日期就得让前端强制弹日期选择器，
或者后端瞎填一个默认值 —— 两种都不好（编一个用户没说的日期，等于造假数据）。

所以规则改成：

- **传了 `date`** → agent 查天气，按天气 + 距离排顺序，返回 `weather_summary`
- **没传 `date`** → agent 跳过天气，只按距离和区域排顺序，`weather_summary` 返回 `null`

前端可以做一个「选择日期（选填）」的入口，用户填了效果更好，不填也能正常出结果。

`travel_matrix` 以后填进来的格式（**现在不用管，后端做好了再说**）：

```json
"travel_matrix": [
  { "from": "Gardens by the Bay", "to": "National Museum of Singapore", "duration_minutes": 18, "distance_km": 2.6 }
]
```

### 返回（下面是真实跑出来的结果，不是设计稿）

```json
{
  "status": "OK",
  "weather_summary": "Rain expected in the afternoon",
  "ordered_stops": [
    {
      "name": "Gardens by the Bay",
      "type": "attraction",
      "lat": 1.2816, "lng": 103.8636,
      "activities": ["take photos"],
      "order": 1,
      "time_of_day": "morning",
      "is_outdoor": true,
      "reason": "Outdoor stop, scheduled for the morning to avoid the rain."
    },
    {
      "name": "National Museum of Singapore",
      "type": "attraction",
      "lat": 1.2966, "lng": 103.8485,
      "activities": ["see the exhibition"],
      "order": 2,
      "time_of_day": "afternoon",
      "is_outdoor": false,
      "reason": "Indoor stop, so the afternoon rain does not matter."
    }
  ],
  "suggested_additions": [
    {
      "name": "Marina Bay Sands",
      "type": "attraction",
      "lat": 1.283, "lng": 103.8585,
      "distance_km": 0.35,
      "similarity": 0.2242,
      "reason": "Close proximity to Gardens by the Bay and offers a similar experience.",
      "activities": ["sightseeing"]
    }
  ]
}
```

### 两个 key 的区别（前端注意，这是两块不同的界面内容）

| | 是什么 | 前端怎么展示 |
|---|---|---|
| `ordered_stops` | **用户自己确认的**地点，被 agent 重排了顺序 | 主行程列表，按 `order` 排，展示 `reason`（"下午有雨，户外的排上午"）和 `time_of_day` 分组 |
| `suggested_additions` | **AI 额外推荐的**新地点，用户没提过 | 单独一块"推荐加入"，用户可选择添加 |

`ordered_stops` 是**带顺序和理由**返回的，不是把用户输入原样回显 —— 这是 agent 和普通推荐的区别。

| 字段 | 说明 |
|---|---|
| `name` / `type` / `lat` / `lng` | 直接来自真实数据集，**不采用模型输出的值**（模型可能抄错） |
| `distance_km` | 到用户已确认地点里最近那个的直线距离 |
| `similarity` | TF-IDF 余弦相似度，方便调试/答辩时解释排序依据 |
| `reason` / `activities` | LLM 针对这个用户写的，上限 255 字（对齐 `draft_place.note`） |
| `weather_summary` | 当天天气总结，来自 data.gov.sg 的 NEA 官方接口。**没传 `date` 或天气查不到时是 `null`** |

### `ordered_stops` ✅ 已实现（2026-08-08）

排序规则，按优先级：

1. **天气优先**：传了 `date` 就查天气 —— 下雨的时段排室内地点，不下雨的时段排室外地点
2. **距离次之**：同一时段内的地点用最近邻串成一条不折返的线路

**降级行为**（很重要，前端要能处理）：

| 情况 | 行为 |
|---|---|
| 没传 `date` | 不查天气，只按距离排，`time_of_day` 全是 `null` |
| 天气接口挂了 / 日期超出 4 天预报范围 | 同上，**不报错** |
| 地点不在我们数据集里（用户自己打的名字） | 保留该地点，`is_outdoor` 为 `null`，不瞎猜 |
| 地点还没有 `lat`/`lng`（后端没地理编码） | 保留该地点，排在有坐标的之后 |

天气是加分项，不是必需品 —— 任何一环拿不到都只是少一个排序依据，不会让整个接口失败。

### 数据来源限制（要如实说明）

数据集 `singapore_attractions.csv` 只有 **107 个地点，全部是 `attraction` 类型**，
没有餐厅、没有酒店。

**所以 `suggested_additions` 只会推荐景点，不会推荐餐厅或住宿。** 这是有意的选择：
推荐的每一个地点都来自政府开放数据、真实存在、带真实经纬度，不是模型凭记忆编的。
餐饮和住宿属于 v1 范围之外。

答辩如果被问到：**"我们推荐的每个地点都是真实的、来自官方数据集，不会编造。餐饮住宿是 v1 范围外的。"**

---

## 5.5 第二层接口三：按天排行程（F-09）✅ 已实现（2026-08-08）

**`POST /plan-itinerary`**

**跟 `/recommend` 的分工（很容易混，这里说死）：**

| 接口 | 管什么 | 做推荐吗 |
|---|---|---|
| `/recommend` | **一天**之内怎么排（`ordered_stops`）+ 推荐新地点 | ✅ 做 |
| `/plan-itinerary` | **多天**怎么分：第 1 天去哪片区、第 2 天去哪片区 | ❌ 不做，要推荐就另调 `/recommend` |

两个接口各管一件事，不绑死 —— 跟当初把 `/extract` 和 `/recommend` 拆开是同一个理由。

**怎么分天**：先把所有地点用最近邻串成一条线路，再按天平均切段。所以同一片区域的
地点会落在同一天，不会一天里横跨全岛。每一天内部照样走"下雨排室内、同时段按距离
串线路"那套单天逻辑（直接复用 `plan_ordered_stops()`）。

> **为什么不用 KMeans 聚类**（答辩大概率会问）：① 聚类不保证每天地点数均衡，
> 真实数据容易分出"第一天 8 个、第二天 1 个"；② KMeans 有随机初始化，同样的输入
> 可能给出不同分组，演示不稳定。切段是确定性的，且每天数量相差不超过 1。
> 代价：切口正好落在两个挨得很近的点之间时，它们会被分到不同天 —— 不是最优分组。

**代码**：[`ML/app/itinerary_planner.py`] `plan_multi_day_itinerary()`

### 请求

```json
{
  "start_date": "2026-08-09",
  "num_days": 3,
  "places": [
    { "name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["photos"] },
    { "name": "Jewel Changi Airport", "type": "attraction", "lat": 1.3601, "lng": 103.9895, "activities": ["waterfall"] }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `places` | 是 | 用户确认后的地点，**形状跟 `/recommend` 完全一样**，后端不用另写 DTO。带 `lat`/`lng` 才能按区域分天 |
| `start_date` | 否 | 第 1 天的日期。传了才查天气；不传就只按距离分，`date`/`weather_summary` 全是 `null` |
| `num_days` | **是** | 排几天，1~30。**2026-08-14 起没有默认值了**（原来漏传会默认 1），漏传直接 400。**从哪来见下面这段** |

#### ⭐ `num_days` 从哪来（2026-08-14 更新，后端接 F-09 前必读）

以前这里是个断头路：抽取结果里根本没有"玩几天"这个信息，后端无论怎么写都造不出
`num_days`。**2026-08-13 在抽取那一侧补上了 `duration_days`**，**2026-08-14 又补上
了 `needs_duration_input`**（见第 4 节字段表），现在这条路完整了。

**完整流程（三端各做哪一段）：**

```
用户输入文本
   ↓  后端调 /extract-travel-info
duration_days = 3, needs_duration_input = false   →  后端直接用 3 当 num_days
duration_days = null, needs_duration_input = true →  前端弹窗问"玩几天？"
                                                     用户答 3 → 后端拿 3 当 num_days
   ↓  后端调 /plan-itinerary（num_days 必填）
按天排好的行程
```

⚠️ **AI 侧的原则是「不猜」**，跟 `coords` 永远返回 `null`、`dates` 不编年份是同一条：
天数说不清就明说说不清，交给用户回答，绝不用一个看起来合理的默认值蒙混过去。所以：

- **不要自己从 `dates` 反推天数** —— `["2026-08-09","2026-08-11"]` 到底是"9号到11号
  玩3天"还是"9号和11号各去一次"，从数组本身分辨不出来，猜错了整个行程的天数就是错的
- **不要在 `needs_duration_input == true` 时偷偷按 1 天排** —— 用户会拿到一个
  他没要求的 1 天行程，而且没有任何地方会报错，只能靠肉眼发现

下面这张表的每一行，2026-08-14 都拿**真实 Bedrock（Nova Lite）** 实跑验证过，
不是照着 prompt 推测的行为：

| 用户输入 | `duration_days` | `needs_duration_input` | 该怎么做 |
|---|---|---|---|
| "A 3-day Singapore trip..." | `3` | `false` | 直接 `num_days=3` |
| "We are spending **five** days..."（英文数字） | `5` | `false` | 直接 `num_days=5` |
| "**A week** in Singapore" | `7` | `false` | 直接 `num_days=7` |
| 正文是 Day 1 / Day 2 / Day 3 结构 | `3` | `false` | 直接 `num_days=3` |
| "from 2026-08-09 **to** 2026-08-11"（连续区间） | `3` | `false` | 直接 `num_days=3` |
| "I want to visit Gardens by the Bay" | `null` | `true` | **前端弹窗问用户**，答案当 `num_days` |
| "I want to visit **5 places**: ..." | `null` | `true` | 地点数**没有**被误当成天数 |
| "a **200-day** trip"（超出 1~30） | `null` | `true` | 见下方「天数超范围」 |

⚠️ 注意倒数第二行和第三行的区别：模型确实分得清"5 个地点"和"玩 5 天"，
但"9号**到**11号"这种连续区间它会算成 3 天 —— 这跟"不要从 `dates` 数组反推"
不冲突，那条禁的是**你们**拿到结构化 JSON 之后自己反推（那时区间信息已经丢了），
而模型是在原文里直接读到"to"这个词的。

**天数超范围（1~30）时会怎样**（2026-08-14 改）：降级成 `null` +
`needs_duration_input: true`，**其余字段照常返回**，跟"没说天数"走同一条路
（前端弹窗问用户）。
以前是抛 schema 校验错误 -> 重试 3 次 -> 整条请求 **502**，连 `destination`
和 `places` 一起丢掉 —— 而重试在这里注定救不回来：模型没抽错，是原文就写着 200，
再问几次答案还是 200。用户看到的是"AI 挂了"，而不是"这个天数不支持"。
日志里会打一行 `[duration] unusable duration_days=...`，联调时按这个关键字搜。

**用户在弹窗里答完之后**，把天数交给后端有两种走法，选一种就行（AI 侧两种都支持）：

1. **推荐**：前端直接把数字随 `/plan-itinerary` 的 `num_days` 传下去，不用再过 AI。
   最省一次模型调用，也最不容易出错。
2. 如果你们的弹窗是走多轮聊天的（把用户的回答 append 成一条 `chat_message` 再调
   `/refine`）：也能用。`chat_filter` 的 prompt 在 2026-08-14 特意加了一条"trip length
   经常是一条很短的独立消息（光一个 `3 days` 甚至光一个 `3`），必须原样保留"，
   否则这种短消息会被当成寒暄过滤掉，`/refine` 出来的 `duration_days` 还是 `null`。

**漏传 `num_days` 会怎样**：`/plan-itinerary` 返回 `400`，`detail` 以
`NUM_DAYS_REQUIRED:` 开头（前缀码稳定，可以直接按前缀分支，不用解析整句话）。
这是故意的 —— 天数是这个接口唯一说不清就没法算的参数，宁可吵也不要静默排错天数。

另外注意 `PlanningService.confirmSession()` 现在写死了 `trip.setDurationDays(1)`，
接 F-09 时这里也要跟着改成真实天数，否则库里存的还是单天行程。

### 返回（下面是真实跑出来的，不是设计稿）

```json
{
  "status": "OK",
  "days": [
    {
      "day": 1,
      "date": "2026-08-09",
      "weather_summary": "No rain expected, good for outdoor stops",
      "stops": [
        { "name": "Gardens by the Bay", "type": "attraction", "lat": 1.2816, "lng": 103.8636,
          "activities": ["photos"], "order": 1, "time_of_day": null, "is_outdoor": true,
          "reason": "Starting point of the route." },
        { "name": "Marina Barrage", "type": "attraction", "lat": 1.2807, "lng": 103.8713,
          "activities": ["kite"], "order": 2, "time_of_day": null, "is_outdoor": true,
          "reason": "About 0.9km from the previous stop, kept next to shorten the route." }
      ]
    },
    { "day": 2, "date": "2026-08-10", "weather_summary": "Partly cloudy and warm", "stops": [] }
  ]
}
```

`stops` 里每一条的字段跟 `/recommend` 的 `ordered_stops` **一模一样**，前端那块 UI 可以直接复用。

### 前端/后端要注意的降级行为

| 情况 | 行为 |
|---|---|
| `days` 的长度 | **永远等于 `num_days`** —— 地点比天数少时，多出来的那天是 `"stops": []` 而不是被删掉，前端可以提示"这天还没安排" |
| 每天的 `order` | **从 1 重新开始**，不是全程连续编号 |
| 第 5 天及以后 | 官方天气只给未来 4 天，之后 `weather_summary` 是 `null`，那几天只按距离排 |
| 没传 `start_date` | 所有 `date` / `weather_summary` / `time_of_day` 都是 `null`，纯按距离分天 |
| 地点没有 `lat`/`lng` | 排在线路最末尾，会落到最后几天，**不会被丢掉** |

### 出错

| 情况 | 状态码 |
|---|---|
| `places` 为空 / `start_date` 格式不是 `YYYY-MM-DD` | **400** |
| `num_days` 不在 1~30（Pydantic 拦下的） | **422** |
| 其它内部失败 | **502** |

⚠️ 参数传错是 **4xx 不是 502** —— 免得后端把"我传错了"误当成"AI 服务挂了"去排查。

---

## 6. 三方分工

### 我（AI）要做的 —— ✅ 全部完成（2026-08-08）

1. ✅ 把解析和推荐拆成两个独立接口（原来绑在一起，用户还没确认就先推荐了）
2. ✅ 距离计算 `geo.py`（haversine，纯数学不调 API）
3. ✅ 天气工具 `weather.py`（data.gov.sg 的 NEA 官方接口，免费免密钥）
4. ✅ 107 个地点的室内/室外标记，已写回 CSV 的 `indoor_outdoor` 列
5. ✅ 组装成 agent：查天气 → 算距离 → 排顺序（`ordered_stops`）→ 推荐（`suggested_additions`）
6. ✅ **F-09 按天拆分行程 `POST /plan-itinerary`**（2026-08-08 补做，见第 5.5 节）

**测试**：83 项全过（推荐器 12、健壮性 9、行程排序 46、天数流程 16）。
排序那些用注入的假天气测的 —— 新加坡不是天天下雨，靠真实接口测不到下雨分支。
接口本身也起服务实跑过（mock 模式），包括 400/422 的参数校验分支。

**还没做的**（不阻塞演示，记录在案）：
- 用后端 Google API 的真实车程替换直线距离（等 `travel_matrix` 传进来，接口格式已经预留好）
- 室内/室外标记没有人工逐条核对，LLM 判的那 22 条有错（见第 8 节）
- 分天用的是"先串线路再切段"，不是最优分组；切口落在两个近点之间时它们会被分到不同天
- 冲突提示（"这两个地点相隔 45 分钟车程"）还没做

### 后端要做的

> **AI 服务这一侧已经全部跑通了**（解析、天气、排顺序、推荐都能用，见上面各节）。
> 现在整条链路卡在后端 —— 下面 🔴 那四条不做的话，**前端页面是空白的**，
> 因为 AI 抽出来的地点从来没有被写进数据库过。

**🔴 不做就没法演示（按顺序做）**

1. **`createSession()` 里调抽取接口** —— 现在这个方法**完全没有调用 AI**，
   只是把用户输入的文字存进 `planning_session.initial_brief` 就返回了
2. **把返回的 places 存成 `DraftPlace`** —— ⚠️ 全后端**没有一行创建 `DraftPlace` 的代码**
   （唯一的 `draftPlaceRepository.save()` 在 `updateDraftPlace()` 里，那是改已有的）。
   这条是最致命的：不存库，前端拿不到任何地点
3. **`AiPlanningClient` 加一个调 `/recommend` 的方法** —— 现在这个接口只有
   `extractTravelInfo()` 和 `generateDailyItinerary()` 两个方法，**没有任何方法能调到推荐接口**。
   即使前面几条都做完，agent 的推荐和排序结果照样出不来。建议签名：
   ```java
   Map<String, Object> recommend(List<PlaceDto> confirmedPlaces, String date, String preferenceText);
   ```
4. **`confirmSession()` 里调用它** —— 现在直接抛 501，把结果（`ordered_stops` +
   `suggested_additions`）存进数据库后返回给前端

**🟡 影响效果但不阻塞**

5. **调 `/recommend` 之前先地理编码**，把 `coords` 补成真实 `lat`/`lng` ——
   否则 agent 没法按距离排序、也没法串线路（不会报错，但排序质量下降）
6. **用 `destination` 字段做范围校验**，非新加坡的请求要拒绝
7. **`AiPlanningClient` 再加一个传聊天记录的方法**才能用 `/refine`（多轮对话完善），
   现在的 `extractTravelInfo(String, String)` 只能传一段文字。建议：
   `refineFromChat(List<ChatMessage> messages, String preferenceText)`

8. **`generateDailyItinerary()` 现在可以真接了** —— `AiPlanningClientHttp.java:102` 那个
   写死返回 `"status":"STUB"` 的方法，注释写的是"Python 侧还没实现按天排程"，
   **这条注释已经过期了：2026-08-08 起就实现了**，改成真的 POST `/plan-itinerary`
   就行（见第 5.5 节）。目前全后端对这个方法**零调用点**，还需要加一个 Controller
   端点才能让前端调到。

   注意现在的签名 `generateDailyItinerary(Long tripId, List<Long> confirmedPlaceIds)`
   传的是**地点 ID**，但 Python 侧不认识你们的 ID，需要后端把 ID 查成带
   `name`/`lat`/`lng` 的地点对象再传。建议签名改成：
   ```java
   Map<String, Object> generateDailyItinerary(List<PlaceDto> confirmedPlaces, String startDate, int numDays);
   ```
   `PlaceDto` 跟调 `/recommend` 用的是同一个，不用另写。
   `numDays` 从 `duration_days` 来，见第 5.5 节的「`num_days` 从哪来」。

**需要三方一起定**

8. `ordered_stops` 和 `suggested_additions` **存哪张表、怎么区分"用户说的"和"AI推荐的"** ——
   我不替后端定数据库设计。另外注意 `reason`/`is_outdoor`/`distance_km` 这几个字段
   `draft_place` 表里目前没有对应列（`reason` 最多塞 `note` VARCHAR(255)）

### 前端要做的

**先看第 2 节** —— 前端调的是 Java 后端（第一层），不是 Python AI 服务。

1. **确认界面** —— 用第 2.3 节的 JSON：每个地点显示名称 + 活动，可编辑（PUT）、可删除（DELETE）
2. **结果界面** —— 用第 2.4 节的 JSON，有三块内容：
   - 顶部展示 `weather_summary`（例如 "Rain expected in the afternoon"）
   - 主行程 `ordered_stops`：按 `order` 排，展示 `reason` 说明为什么这么安排，
     可以按 `time_of_day`（morning/afternoon/evening）分组显示
   - 底部 `suggested_additions`：可选择添加的推荐地点
3. ⚠️ **`weather_summary` 和 `time_of_day` 可能是 `null`**（没传日期、或天气查不到时），
   界面要能处理这种情况 —— 那时就是纯按距离排的一条线路，没有时段分组
4. `destination` 字段不用展示，它只是给后端做范围校验用的
5. 编辑地点时**不会触发重新解析**（见 2.2），改完直接生效，不用等 AI
6. `place_id` 是编辑/删除时定位用的，务必保留

---

## 7. 出错时返回什么

⚠️ **两层的错误格式不一样，别搞混**（之前这里写错了，前端反馈后已更正）。

### 给前端：Java 后端的错误格式

前端只会看到这个格式（`ErrorResponse.java`）：

```json
{
  "timestamp": "2026-08-07T09:12:33Z",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": ["field: 错误说明"]
}
```

`GlobalExceptionHandler` 会把所有异常都包成这个形状，包括 AI 服务挂掉的情况。
**前端照这个写错误提示，不要用下面 Python 那个格式。**

### 给后端：Python AI 服务的错误格式

后端调 Python 时会看到这个，需要自己转换成上面的 Java 格式再给前端：

```json
{ "detail": "错误信息文字..." }
```

失败时 HTTP 状态码是 **502**。

### 两层都适用的一点

返回体里的 `status` 字段**永远是 `"OK"`**，不会用来表示错误 ——
出错时压根走不到组装返回体那一步，直接抛异常。
判断是否出错请看 **HTTP 状态码**，不要写 `if (body.status !== "OK")`。

---

## 8. 还没解决 / 待讨论

### 前端 review 后发现的后端缺口（2026-08-07，需后端确认）

这几条是前端对着 Java 代码核对文档时发现的，**都属实**，需要后端补：

| # | 缺什么 | 现状 | 影响 |
|---|---|---|---|
| 1 | `POST /planning-sessions` 不返回地点 | 只返回 `PlanningSessionSummaryResponse`（id/title/status/…），没有 `places` | 前端拿不到确认界面要展示的数据，2.3 节那个返回体需要后端补上 |
| 2 | `POST /{sessionId}/confirm` 还是 501 | `PlanningService.confirmSession()` 直接抛 NOT_IMPLEMENTED | 前端拿不到 agent 结果，整个结果界面没法做 |
| 3 | 改不了 `activities` | `UpdateDraftPlaceRequest` 只有 name/address/lat/lng/category/note | 用户改不了"在这个地点做什么"。注意 `activities` 存在**另一张表** `draft_activity`，不是给 PUT 加个字段就行，需要单独的接口 |
| 4 | 没有"把推荐地点加进行程"的接口 | `suggested_additions` 前端能展示，但用户点了"加入"之后没接口可调 | 需要类似 `POST /planning-sessions/{id}/draft-places` 的接口，把推荐的地点写成 `draft_place` |

第 3 条要特别注意：`draft_activity` 是独立的表，通过 `draft_place_id` 关联到地点。
所以"编辑活动"跟"编辑地点"是两件事，接口也得分开。

### ⚠️ 本地模型处理中文输入会出乱码（2026-08-08 实测发现）

拿组长的中文例子端到端跑 `/extract-travel-info`，本地
`llama3.1:8b-instruct-q4_K_M` 抽出来的地点名是**乱码**：

| 输入 | 抽出的地点名 |
|---|---|
| 我想去滨海湾花园拍照，然后去国家博物馆看展览 | `次公南关学公家`、`园海关学公家` ❌ |
| I want to take photos at Gardens by the Bay, then visit the National Museum of Singapore | `Gardens by the Bay`、`National Museum of Singapore` ✅ |

同一份代码、同一个模型，**英文完全正常，中文出乱码**。这是 8B 量化模型的中文
能力局限，不是代码 bug（JSON 结构、type 枚举、activities 都是对的，只有中文
名字本身是乱的）。

**影响**：如果 demo 要用中文输入，本地模型这条路现在跑不通。

**已经排除的一个假设**：一开始怀疑是 prompt 里混了中文（schema 的 description
原本是中文，会被序列化进 system prompt）诱导模型往中文输出偏。
2026-08-08 把 `trip_schema.json`、`trip_models.py`、`recommend_agent.py` 里所有
会进 prompt 的 description 全部改成英文后重测，**中文输入照样乱码**。
所以这是模型本身的中文能力问题，不是 prompt 语言问题。这条对照实验值得写进答辩。

**可选的解决方向**（还没定，需要讨论）：

1. 切 Bedrock Nova Lite（任务 2 选型的赢家）跑一次中文测试，看是不是好很多 ——
   `EXTRACT_PROVIDER=bedrock` 就能切，代码不用改
2. 换一个中文能力强的本地模型（比如 Qwen 系列）
3. Demo 统一用英文输入 ← **目前采用这条**，所有测试/演示数据已经统一改成英文

**答辩如果被问**：如实说"本地小模型对中文的抽取会产生乱码地名，英文正常，
这是模型能力限制不是流程问题，切云端模型或换中文模型可以解决，还没做对比测试"。

### 其它待办

- **天气接口要先验证**：data.gov.sg 的 NEA 天气 API 具体返回格式，写代码前要先实际调一次确认
- **`travel_matrix` 的时机**：后端 Google API 什么时候能好？没好之前 agent 用直线距离，
  排序准确度会差一些（直线距离没考虑跨海、地铁线路等）
- **Python 服务没有鉴权**：本地开发无所谓，正式部署前要加
- **数据库存储设计**：`ordered_stops` / `suggested_additions` 存哪、怎么区分，待三方讨论

---

## 9. 明确不做（v1 范围外）

- 具体到几点几分的排程 —— 数据集没有营业时间，排出来是编的
- 餐厅、酒店推荐 —— 数据集里没有这类数据

> ~~按天排行程（第一天去哪、第二天去哪）~~ —— **2026-08-08 已实现**，见第 5.5 节
> `POST /plan-itinerary`。原来按组长的单天例子定的范围，后来对照 backlog 发现
> F-09 本身就是"按天划分的行程"，属于 Must，所以补上了。
