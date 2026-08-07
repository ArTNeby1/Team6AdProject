# AI 服务设计与接口说明 —— 前端 / 后端 / AI 三方对齐用

这份文件是这条流程的**唯一参考**：接口叫什么、传什么、返回什么、谁负责哪一段。
三个人照这份文件各自动手写代码，不用再互相猜格式。

> **状态说明**：文件里每个接口都标了 ✅（已写好能跑）或 🟡（设计已定、代码待写）。
> 🟡 的接口格式已经定下来了，前端后端可以先照着写自己那边的代码，不用等我。

---

## 1. 整体流程

组长给的方向：

> 输入："我今天要去iss学习然后去圣淘沙游泳"
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
│ 接口二  POST /recommend 🟡  │  agent：查天气 + 算距离 + 排顺序 + 推荐
└─────────────────────────────┘
    │
    ▼  后端存进数据库
```

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

| 步骤 | 方法 | 路径 | 后端内部做什么 |
|---|---|---|---|
| ① 用户输入文字 | POST | `/api/v1/planning-sessions` | 建会话 → 调 Python `/extract-travel-info` → 地理编码 → 存 `draft_place` → 返回给前端 |
| ② 用户改地点名 | PUT | `/api/v1/planning-sessions/draft-places/{placeId}` | 直接改数据库，**不重新调 LLM** |
| ③ 用户删地点 | DELETE | `/api/v1/planning-sessions/draft-places/{placeId}` | 直接删，**不重新调 LLM** |
| ④ 用户补充说明 | POST | `/api/v1/planning-sessions/{sessionId}/messages` + `/refine` | 调 Python `/refine` 重新解析 |
| ⑤ 用户点确认 | POST | `/api/v1/planning-sessions/{sessionId}/confirm` | 调 Python `/recommend` → 存结果 → 返回 |

**②③④ 已经存在**（`PlanningController.java` 里有），①⑤ 目前是 501 占位。

### 2.2 编辑时要不要重新解析？—— 不要

这是个容易踩坑的设计点，定死规则：

| 用户行为 | 处理方式 | 为什么 |
|---|---|---|
| 改名字 / 删地点 / 改活动 | **直接改数据库，不调 LLM** | 用户是在**纠正** LLM 的错误，再跑一次 LLM 很可能把用户的修改又覆盖回去 |
| 又打了一段新文字 | 调 `/refine` 重新解析 | 这是新信息，需要 LLM 理解 |

### 2.3 前端在「确认界面」拿到的数据

后端 ① 返回给前端的，应该是补完经纬度之后的地点列表。字段来自 Python 接口一（第 4 节），
外加后端补的 `place_id` 和真实坐标：

```json
{
  "session_id": 17,
  "places": [
    { "place_id": 101, "name": "滨海湾花园", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["拍照"] },
    { "place_id": 102, "name": "国家博物馆", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["看展览"] }
  ]
}
```

⚠️ 跟 Python 直接返回的差别：**多了 `place_id`**（编辑/删除要用它定位）、
**`coords: null` 变成了真实的 `lat`/`lng`**（后端地理编码补的）。

### 2.4 前端在「结果界面」拿到的数据

后端 ⑤ 返回的，就是 Python 接口二（第 5 节）的返回体：
`weather_summary` + `ordered_stops` + `suggested_additions`。
后端可能会额外加 `place_id` 之类的字段方便前端操作，具体由后端定。

---

## 3. Agent 设计 —— 它到底做什么

组长只说了"agent 生成建议"，这里把内部定清楚。

**先说明**：这里的 "agent" 指"一个会调用工具、自己决定怎么做的服务"，
**不是** AWS Bedrock Agents 那个具体产品（控制台配 Action Group + Lambda 那一整套）。
项目没有要求必须用那个服务，用它要多开好几个 AWS 资源，这次不需要。
模型本身还是用任务 2 选定的 Bedrock Nova Lite（见 `model_selection.md`）或本地 Ollama。

### Agent 的工具

| 工具 | 做什么 | 数据来源 | 状态 |
|---|---|---|---|
| `search_places` | 从 107 个真实新加坡景点里找相似的 | `singapore_attractions.csv` | ✅ `content_recommender.py` 已写好 |
| `get_distance` | 算两个地点之间的直线距离 | 经纬度做 haversine 计算，纯数学不调 API | 🟡 约 20 行 |
| `group_by_area` | 把邻近的地点归到一组，避免来回横跨全岛 | 同上 | 🟡 约 30 行 |
| `get_weather` | 查当天/未来天气预报 | data.gov.sg 的 NEA 天气接口（免费、免密钥） | 🟡 用之前先验证接口格式 |

**一次性准备工作（不是运行时工具）**：给 107 个地点各打一个"室内/室外"标记。
让 LLM 把数据集里的 description 过一遍分类，结果存回 CSV。
**没有这个标记，天气数据是废的** —— 知道下午下雨但不知道哪些地方是室内的，agent 没法据此做决定。

### Agent 能做的四件事

1. **按天气排顺序** —— 下午有雨 → 户外景点排上午，室内的排下午
2. **按地理位置排顺序** —— 邻近的排一起，不要圣淘沙 → 樟宜 → 圣淘沙来回跑
3. **补充推荐** —— 用户只说了 2 个地点，一天还有空，从数据集里推荐附近的
4. **提示冲突** —— "这两个地点相隔 45 分钟车程，一个下午安排得比较紧"

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
```
uvicorn main:app --reload --app-dir ML/app --port 8001
```
（`http://localhost:8001/docs` 有 FastAPI 自动生成的交互式测试页）

### 请求

```json
{ "raw_content": "我想去滨海湾花园拍照，然后去国家博物馆看展览", "source_url": null }
```

`/refine` 的请求换成聊天记录：
```json
{
  "messages": [
    { "role": "user", "content": "嗨你好呀" },
    { "role": "user", "content": "我想去滨海湾花园拍照，然后去国家博物馆看展览" }
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
  "places": [
    { "name": "滨海湾花园", "type": "attraction", "coords": null, "activities": ["拍照"] },
    { "name": "国家博物馆", "type": "attraction", "coords": null, "activities": ["看展览"] }
  ]
}
```

**这就是前端做确认界面要用的 JSON。** 每个地点自带自己的活动列表（`滨海湾花园` 配 `拍照`、
`国家博物馆` 配 `看展览`），不是两个分开的列表让前端自己配对。

### 字段说明

| 字段 | 说明 |
|---|---|
| `destination` | **前端不用展示**。它的作用是范围校验：这个 app 只做新加坡，如果用户说"我想去曼谷"，这里会是 `"Bangkok"`，后端据此拒绝并提示用户。 |
| `dates` | 文本里明确写了日期才有，没写就是空数组 `[]` —— 不让模型编年份。 |
| `coords` | **永远是 `null`**，是故意的。模型禁止编坐标，真实经纬度由后端 `MapPlacesClient` 查地图 API 补上。 |
| `type` | 枚举：`attraction` / `restaurant` / `hotel` / `market` / `other` |

---

## 5. 第二层接口二：推荐 🟡 设计已定，代码待写

**`POST /recommend`**

**什么时候调**：用户确认地点之后、**且后端已经补好经纬度之后**。

### 请求

```json
{
  "date": "2026-08-07",
  "places": [
    { "name": "滨海湾花园", "type": "attraction", "lat": 1.2816, "lng": 103.8636, "activities": ["拍照"] },
    { "name": "国家博物馆", "type": "attraction", "lat": 1.2966, "lng": 103.8485, "activities": ["看展览"] }
  ],
  "travel_matrix": null,
  "preference_text": null
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `date` | 是 | 查天气要用。没有日期就查不了预报。 |
| `places` | 是 | 用户确认后的地点，**必须已经有 `lat`/`lng`** |
| `travel_matrix` | 否 | 后端 Google API 做好之后填这里（见下），现在传 `null` 就行 |
| `preference_text` | 否 | 用户偏好，比如 `"travel_style=culture"` |

`travel_matrix` 以后填进来的格式（**现在不用管，后端做好了再说**）：
```json
"travel_matrix": [
  { "from": "滨海湾花园", "to": "国家博物馆", "duration_minutes": 18, "distance_km": 2.6 }
]
```

### 返回

```json
{
  "status": "OK",
  "weather_summary": "今天下午 2-5 点有阵雨，其余时段多云",
  "ordered_stops": [
    {
      "name": "滨海湾花园",
      "order": 1,
      "time_of_day": "morning",
      "is_outdoor": true,
      "reason": "下午有阵雨，户外的花园排在上午拍照光线也更好"
    },
    {
      "name": "国家博物馆",
      "order": 2,
      "time_of_day": "afternoon",
      "is_outdoor": false,
      "reason": "室内展馆，下午下雨也不影响"
    }
  ],
  "suggested_additions": [
    {
      "name": "新加坡植物园",
      "type": "attraction",
      "lat": 1.3138,
      "lng": 103.8159,
      "is_outdoor": true,
      "distance_km": 5.4,
      "reason": "跟滨海湾花园同为户外拍照景点，距离不远，可以接在上午的行程之后",
      "activities": ["散步", "拍照"]
    }
  ]
}
```

### 两个 key 的区别（前端注意）

| | 是什么 | 前端怎么展示 |
|---|---|---|
| `ordered_stops` | **用户自己确认的地点**，被 agent 重新排了顺序 | 主行程列表，按 `order` 排，展示 `reason` 解释为什么这么排 |
| `suggested_additions` | **AI 额外推荐的**新地点，用户没提过 | 单独一块"推荐加入"，用户可以选择添加 |

注意 `ordered_stops` 里是**带顺序和理由**返回的，不是把用户输入原样回显 ——
这是"agent"和"普通推荐"的区别。

### 数据来源限制（要如实说明）

数据集 `singapore_attractions.csv` 只有 **107 个地点，全部是 `attraction` 类型**，
没有餐厅、没有酒店。

**所以 `suggested_additions` 只会推荐景点，不会推荐餐厅或住宿。** 这是有意的选择：
推荐的每一个地点都来自政府开放数据、真实存在、带真实经纬度，不是模型凭记忆编的。
餐饮和住宿属于 v1 范围之外。

答辩如果被问到：**"我们推荐的每个地点都是真实的、来自官方数据集，不会编造。餐饮住宿是 v1 范围外的。"**

---

## 6. 三方分工

### 我（AI）要做的

1. 🟡 把 `/recommend` 从 `run_pipeline()` 里拆出来，做成独立接口（现在解析和推荐是绑一起跑的，
   跟"用户确认后才推荐"的流程不符）
2. 🟡 写 `get_distance`（haversine）和 `group_by_area`
3. 🟡 接 data.gov.sg 天气接口
4. 🟡 给 107 个地点打室内/室外标记，存回 CSV
5. 🟡 把上面这些组装成 agent：查天气 → 算距离 → 排顺序 → 生成推荐理由

### 后端要做的

1. 把 `AiPlanningClientStub` 换成真的 HTTP 调用，指向 `http://localhost:8001`
2. **⚠️ 字段名对不上**：stub 现在假设返回 `{status, places, activities}`，
   实际是 `{status, destination, dates, places}` —— 照这份文件的真实格式改
3. **⚠️ 接口缺方法**：`AiPlanningClient.java` 现在只有 `extractTravelInfo(String, String)`，
   传不了聊天记录，用不了 `/refine`。要支持多轮聊天需要加一个方法，比如
   `refineFromChat(List<ChatMessage> messages, String preferenceText)`
4. 调 `/recommend` **之前**必须先地理编码，把 `coords` 补上 —— 否则 agent 没法按距离排序
5. 用 `destination` 字段做范围校验，非新加坡的请求要拒绝
6. 把 `ordered_stops` 和 `suggested_additions` 存进数据库
   —— **存哪张表、怎么区分"用户说的"和"AI推荐的"，由后端决定**，
   我不替后端定数据库设计，需要三个人一起讨论

### 前端要做的

**先看第 2 节** —— 前端调的是 Java 后端（第一层），不是 Python AI 服务。

1. **确认界面** —— 用第 2.3 节的 JSON：每个地点显示名称 + 活动，可编辑（PUT）、可删除（DELETE）
2. **结果界面** —— 用第 2.4 节的 JSON：`ordered_stops` 是主行程（按 `order` 排，展示 `reason`
   说明为什么这么安排），`suggested_additions` 是可选择添加的推荐
3. `destination` 字段不用展示，它只是给后端做范围校验用的
4. 编辑地点时**不会触发重新解析**（见 2.2），改完直接生效，不用等 AI
5. `place_id` 是编辑/删除时定位用的，务必保留

---

## 7. 出错时返回什么

两个接口失败时都返回 **HTTP 502**：
```json
{ "detail": "错误信息文字..." }
```

**前端注意**：返回体里的 `status` 字段**永远是 `"OK"`**，不会用来表示错误 ——
出错时压根走不到组装返回体那一步，直接抛 HTTP 异常。
判断是否出错请看 **HTTP 状态码**，不要写 `if (body.status !== "OK")`。

---

## 8. 还没解决 / 待讨论

- **天气接口要先验证**：data.gov.sg 的 NEA 天气 API 具体返回格式，写代码前要先实际调一次确认
- **`travel_matrix` 的时机**：后端 Google API 什么时候能好？没好之前 agent 用直线距离，
  排序准确度会差一些（直线距离没考虑跨海、地铁线路等）
- **Python 服务没有鉴权**：本地开发无所谓，正式部署前要加
- **数据库存储设计**：`ordered_stops` / `suggested_additions` 存哪、怎么区分，待三方讨论

---

## 9. 明确不做（v1 范围外）

- 按天排行程（第一天去哪、第二天去哪）—— 组长的例子是单天场景，先不做
- 具体到几点几分的排程 —— 数据集没有营业时间，排出来是编的
- 餐厅、酒店推荐 —— 数据集里没有这类数据
