# 游玩天数：没说就问用户（AI 侧已完成，2026-08-14）

> 一句话：**AI 侧不猜天数**。抽到了就给数字，抽不到就明确告诉你"去问用户"，
> 绝不用一个看起来合理的默认值蒙混过去。
>
> 完整字段格式见 `ML/docs/ai_contract.md`（三方唯一参考），这份只讲这次改了什么、
> 你们各自要做什么。

---

## 一、AI 侧改了什么

| # | 改动 | 影响谁 |
|---|---|---|
| 1 | `/extract-travel-info`、`/refine` 返回体新增 **`needs_duration_input`**（布尔，恒定存在） | **前端**（弹窗判断） |
| 2 | 🔴 `/plan-itinerary` 的 **`num_days` 变必填**（原来漏传默认 1） | **后端** |
| 3 | `chat_filter` 降噪 prompt 保留"3 days"这种超短消息 | 只有走 `/refine` 传弹窗答案时才相关 |
| 4 | mock 模式的 `duration_days` 改成看输入文本 | 前端 + 后端联调 |

`duration_days` 字段本身是 2026-08-13 加的，这次没动。

---

## 二、完整流程（三端各做哪一段）

```
用户输入文本
      │
      │  后端调 AI: POST /extract-travel-info
      ▼
┌─────────────────────────────────────────────┐
│ needs_duration_input == false               │  →  后端直接用 duration_days 当 num_days
│   (duration_days = 3)                       │
├─────────────────────────────────────────────┤
│ needs_duration_input == true                │  →  前端弹窗："这趟打算玩几天？"
│   (duration_days = null)                    │      用户答 3 → 当 num_days
└─────────────────────────────────────────────┘
      │
      │  后端调 AI: POST /plan-itinerary  { places, start_date, num_days }
      ▼
按天排好的行程（days 长度恒等于 num_days）
```

---

## 三、前端同学要做的

### 1. 读 `needs_duration_input` 决定弹不弹窗

`/extract-travel-info` 和 `/refine` 的返回体现在长这样（多了最后一个字段）：

```json
{
  "status": "OK",
  "destination": "Singapore",
  "dates": [],
  "duration_days": null,
  "needs_duration_input": true,
  "places": [ ... ]
}
```

- `needs_duration_input: true` → 弹窗问用户玩几天，**拿到答案才能继续排行程**
- `needs_duration_input: false` → 不弹，`duration_days` 直接就是天数

这个字段**恒定存在**，不是"缺天数时才有"，可以无脑 `if (res.needs_duration_input)`，
不用担心读到 `undefined`。

### 2. 弹窗输入建议做成数字输入（1~30）

上下限跟 AI 侧对齐（`itinerary_planner.MAX_DAYS = 30`），超出范围 `/plan-itinerary`
会返回 400。

### 3. 用户答完之后，天数怎么传下去 —— 选一种

**推荐**：前端直接把数字交给后端，随 `/plan-itinerary` 的 `num_days` 传下去，
不用再过一次 AI。最省一次模型调用，也最不容易出错。

**另一种**（只有你们的弹窗本来就是走多轮聊天时才用）：把用户回答 append 成一条
`chat_message` 再调 `/refine`，AI 会重新抽一次 `duration_days`。这条路现在也通了 ——
`chat_filter` 特意改过，"3 days" 甚至光一个 "3" 这种超短消息不会再被当寒暄过滤掉。

### 4. ⚠️ 还有一个前端侧的缺口（不是这次 AI 改动引入的）

后端的 `POST /api/v1/trips/{tripId}/generate`（就是触发按天拆分的那个端点）
**目前全前端零调用点** —— 我 `git grep` 过 `Frontend_Web/src` 和 `Frontend_Android`，
一个都没有。所以这个功能现在在界面上根本触发不了，需要前端加入口。

---

## 四、后端同学要做的

### 1. 🔴 `num_days` 现在是必填（不兼容改动，但大概率不影响你）

`/plan-itinerary` 原来 `num_days` 有默认值 1，漏传会**静默**排成 1 天行程并返回 200 ——
用户明明说了玩 3 天，结果拿到 1 天，全链路没有任何地方会报错。现在漏传直接：

```
HTTP 400
detail: "NUM_DAYS_REQUIRED: num_days is required and has no default. ..."
```

前缀码 `NUM_DAYS_REQUIRED:` 是稳定的，可以直接按前缀分支，不用解析整句话。

**你现在的代码不受影响**：`AiPlanningClientHttp.planItinerary()` 一直是
`body.put("num_days", numDays)` 显式传值的。这条只挡"以后有人忘了接"的情况。

### 2. `confirmSession()` 里写死的 `setDurationDays(1)` 要改

`PlanningService.java` 里这一行（`origin/main` 上还在）：

```java
trip.setDurationDays(1);
```

只要它还写死 1，库里存的永远是单天行程 —— 后面 `generateItinerary()` 拿
`trip.getDurationDays()` 当 `num_days`，也就永远是 1，等于整条多天链路白做。

抽取结果里现在有天数了，串起来大概是：

```java
// duration_days 可能为 null（needs_duration_input=true，前端会问用户）
Integer extracted = extraction.getDurationDays();
trip.setDurationDays(extracted != null ? extracted : userAnsweredDays);
```

`userAnsweredDays` 从哪来取决于前端怎么传（见上面第三节第 3 点），这块要你们俩对一下。
建议顺便把建 `TripDay` 的地方也从"固定建 1 个"改成按天数建 N 个，否则
`generateItinerary()` 里 `findByTrip_IdAndDaySequence()` 会找不到第 2、3 天而抛
`TRIP_DAY_NOT_FOUND`。

### 3. ⚠️ 不要自己从 `dates` 反推天数

`["2026-08-09", "2026-08-11"]` 到底是"9号到11号玩3天"还是"9号和11号各去一次"，
从数组本身分辨不出来，猜错了整个行程的天数就是错的。天数只有两个合法来源：
`duration_days`，或者用户在弹窗里的回答。

---

## 五、联调怎么测（不用装 Ollama，不用 AWS）

```powershell
$env:EXTRACT_PROVIDER="mock"; $env:FILTER_PROVIDER="mock"; $env:RECOMMEND_PROVIDER="mock"
uvicorn main:app --reload --app-dir ML/app --port 8001
```

mock 模式下 JSON 结构跟真实模式**一模一样**，字段一个不少，而且秒回。

**这次特意改了 mock**：以前 `duration_days` 恒定返回 2，等于"没天数要弹窗"那条分支
在 mock 下**永远测不到**。现在 mock 会真的看你传的文本，两条分支都能测：

| 传的 `raw_content` | `duration_days` | `needs_duration_input` | 测的是哪条分支 |
|---|---|---|---|
| `"a 3-day trip in Singapore"` | `3` | `false` | 有天数，直接排 |
| `"我想去新加坡玩5天"` | `5` | `false` | 有天数，直接排 |
| `"Day 1: Gardens. Day 2: Sentosa."` | `2` | `false` | 有天数，直接排 |
| `"I want to see Gardens by the Bay"` | `null` | **`true`** | **没天数，弹窗** |

浏览器打开 `http://localhost:8001/docs` 可以直接点着试，不用写代码。

---

## 六、AI 侧的自测

```
python ML/app/test_duration_flow.py     # 16 项，这次新增，不需要 AWS/Ollama
python ML/app/test_robustness.py        # 9 项
python ML/app/test_itinerary_planner.py # 46 项
```

2026-08-14 本机跑过，全绿。覆盖的点：两条分支各自的 `needs_duration_input` 取值、
`needs_duration_input` 恒定存在、漏传 `num_days` 返回 400、给了 `num_days` 就真的
按那个天数排（`len(days) == num_days`）、天数越界被 schema 拦下。
