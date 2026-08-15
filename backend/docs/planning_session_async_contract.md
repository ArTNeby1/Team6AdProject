# Planning Session 异步导入契约 —— Web / Mobile 都要看

`POST /api/v1/planning-sessions`（Smart AI Import 的入口）是**异步**的：接口立刻返回，
真正的 AI 抽取在后台跑。这份文档就是写给两边客户端的"该怎么等结果"，因为这条坑
web 端和 mobile 端已经各自独立踩过一次了（现象一样：建 session 后立刻查，
`draftPlaces` 是空的，被误判成"AI 挂了"或"接口没连上"）。

> 背景：`createSession()` 以前是同步的，AI 抽取跑多久接口就挂多久。后来改成
> `@Async` 后台处理，接口不再等 AI，好处是不会因为 Bedrock 偶尔慢而超时，
> 代价是客户端不能再假设"拿到 201 就等于拿到结果"。

## 1. 完整流程

```
POST /planning-sessions  { initialBrief }
   ↓ 立刻返回（通常 <200ms，不等 AI）
201 { id, status: "PROCESSING", draftPlaces: [] }
   ↓
   （后台线程调 ML /extract-travel-info，通常几秒，Bedrock 慢的时候可能更久）
   ↓
GET /planning-sessions/{id} 轮询，直到状态不再是 PROCESSING：

   status: "DRAFT_READY"  → draftPlaces 有数据，可以进确认页
   status: "FAILED"       → failureReason 有文案，直接展示给用户，不要当系统错误弹窗
```

`status: "PROCESSING"` 时 `draftPlaces` 恒为空数组，这是正常状态，**不代表出错**。

## 2. 状态机

| status | 含义 | 客户端该做什么 |
|---|---|---|
| `PROCESSING` | 后台还在跑 AI 抽取 | 继续轮询，别报错 |
| `DRAFT_READY` | 抽取成功，`draftPlaces` 已经有数据 | 停止轮询，进确认/编辑页 |
| `FAILED` | 抽取失败（见下方 `failureReason`） | 停止轮询，把 `failureReason` 原样/翻译展示给用户，允许重新输入 |
| `ACTIVE` | 单纯建了个空 session（`initialBrief` 是空的），还没触发抽取 | 不会自动变成 `DRAFT_READY`，等用户发消息走 `/refine` |
| `CONFIRMED` | 用户已确认生成行程 | 与本文档无关 |

## 3. 轮询建议

- 间隔：2 秒一次
- 超时：60 秒还没结束（实测正常情况 3~8 秒内会出结果），当成 `FAILED` 处理，
  提示用户"导入超时，请重试"——这种情况通常是 Bedrock 侧临时抖动，不是 bug
- 拿到 `DRAFT_READY` 或 `FAILED` 立刻停止轮询，不要等到超时

实测过一次真实调用（2026-08-16，线上环境，真实 Bedrock）：
`POST` 后 5 秒内 `status` 就变成了 `DRAFT_READY`，9 个地点全部落库，可以作为
"正常情况下大概多久"的参考。

## 4. `FAILED` 时 `failureReason` 的可能取值

`failureReason` 是英文文案，可以直接展示或者客户端自己套一层 i18n：

| 场景 | `failureReason` |
|---|---|
| 输入没有可提取的旅行信息（废话/无关内容） | `We could not find usable travel details in that text.` |
| 目的地不在新加坡 | `This app only plans trips within Singapore.` |
| AI 服务本身调用失败/超时/返回空 | `The import service could not extract places right now. Please try again.` |
| 其它未分类异常 | `We could not finish importing your travel notes.` |

同一时刻还会写一条 `UserNotification`（`type: IMPORT_FAILED`），如果客户端已经接了
`GET /api/v1/notifications`，也可以用通知列表代替轮询——但轮询 `GET /planning-sessions/{id}`
更简单直接，两种方式选一种就行，不用都做。

## 5. 不受这条异步规则影响的接口

`/refine`、`/validate-places`、`/confirm` 这几个接口都是**同步**的，调用方直接拿
返回体里的结果用，不用轮询。只有 `createSession()`（即 `POST /planning-sessions`
本身）是异步的——这是唯一的例外，也是这份文档只写它的原因。
