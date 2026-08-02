# LoomyTrip 数据库详细字段说明

> 数据库名：**`LoomyTrip`**  
> 以下类型、可否为空、默认值、主键/外键以 **目标 MySQL 库设计** 为准（可用 `DESCRIBE 表名;` 核对）。  
> 连接：`127.0.0.1:3306`，库名 `LoomyTrip`。  
> 产品路径：**用户给出粗略路线 → 与 AI Agent 多轮完善 → 确认后生成正式行程**。

---

## 总览

| 对象 | 类型 | 说明 |
|---|---|---|
| `users` | 表 | App 旅行者账号 |
| `admin` | 表 | 后台管理员账号（独立登录） |
| `planning_session` | 表 | 一次 AI 规划会话（含粗略路线 brief） |
| `chat_message` | 表 | 会话中的每轮对话 |
| `draft_place` | 表 | 规划中的草稿地点（确认前可改可删） |
| `draft_activity` | 表 | 规划中的草稿活动/安排 |
| `trip` | 表 | 确认后的正式旅行计划 |
| `trip_preference` | 表 | **每次行程**的偏好（与 trip 一对一；推荐依据） |
| `trip_day` | 表 | 行程中的「第几天」 |
| `trip_schedule` | 表 | 某一天内的停点 |
| `trip_transport` | 表 | 两个停点之间的交通 |
| `destination` | 表 | 地点/景点主数据 |
| `comment` | 表 | 地点评论与评分 |
| `v_trip_day_calendar` | **视图** | 由 start_date + day_sequence 算出日历日 |

**不设 `user_preference`**：偏好按次旅行存在 `trip_preference`，推荐也基于当前行程偏好。

**关系简图**

```text
【规划域 — 草稿】
users ──< planning_session ──< chat_message
  │              │
  │              ├──< draft_place ──?> destination
  │              └──< draft_activity >── draft_place
  │              │
  │              └── confirmed_trip_id ──?> trip   （确认后关联）

【正式行程域】
users ──< trip ───1:1── trip_preference
  │         │
  │         └──< trip_day ──< trip_schedule >── destination
  │                    │              │
  │                    └──< trip_transport（prev/next → trip_schedule）
  └──< comment >── destination

admin（无外键，仅后台登录）
```

**规划 → 正式行程（重要）**  
1. 创建 `planning_session`，写入 `initial_brief`（粗略路线）  
2. 多轮聊天写入 `chat_message`；AI 维护 `draft_place` / `draft_activity`  
3. 用户确认后：创建 `trip`（及 day/schedule/preference 等）  
4. 回写 `planning_session.confirmed_trip_id = trip.id`，`status = CONFIRMED`  
5. 确认前 `confirmed_trip_id` 为空；草稿与正式行程分离，避免污染正式数据  

**日期规则（重要）**  
- 正式行程不存「每一天的 date」  
- `calendar_date = start_date + (day_sequence - 1)`  
- `trip_end_date = start_date + (duration_days - 1)`  
- 查日历请用视图 `v_trip_day_calendar`

---

## 类型速查（给不熟 MySQL 的同学）

| MySQL 类型 | 含义 |
|---|---|
| `bigint unsigned` | 无符号大整数，常用作自增主键 / 外键 |
| `int unsigned` | 无符号整数（天数、分钟、序号等） |
| `tinyint(1)` | 常用作布尔：0/1 |
| `decimal(M,D)` | 定点数；如 `decimal(10,7)` 坐标，`decimal(2,1)` 评分 |
| `varchar(N)` | 可变长字符串，最长 N 字符 |
| `text` / `mediumtext` | 长文本 |
| `date` | 日期，格式 `YYYY-MM-DD` |
| `time` | 时刻，格式 `HH:MM:SS` |
| `timestamp` | 日期时间；可自动填当前时间 |
| `enum(...)` | 只能取列出的几个固定值 |

列说明中的缩写：

| 标记 | 含义 |
|---|---|
| **PK** | 主键 Primary Key |
| **FK** | 外键 Foreign Key |
| **UQ** | 唯一 Unique |
| **AI** | 自增 AUTO_INCREMENT |
| **NULL** | 允许为空 |
| **NOT NULL** | 不允许为空 |

---

## 1. 表 `users`（旅行者）

App 端登录与个人资料。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 用户主键 |
| `email` | `varchar(255)` | NOT NULL | **UQ** | — | | 登录邮箱，全局唯一 |
| `password_hash` | `varchar(255)` | NOT NULL | | — | | 密码哈希，禁止存明文 |
| `age` | `int unsigned` | **NULL** | | NULL | | 年龄，可选 |
| `gender` | `varchar(32)` | **NULL** | | NULL | | 性别，可选 |
| `role` | `enum('traveler','admin')` | NOT NULL | | `'traveler'` | | 历史字段；**后台请用 `admin` 表** |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 注册/建号时间 |

**外键（本表被引用）**  
- `trip.user_id` → `users.id`  
- `planning_session.user_id` → `users.id`  
- `comment.user_id` → `users.id`

---

## 2. 表 `admin`（后台管理员）

Admin Console 登录；与 `users` **无外键关系**。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 管理员主键 |
| `email` | `varchar(255)` | NOT NULL | **UQ** | — | | 后台登录邮箱 |
| `password_hash` | `varchar(255)` | NOT NULL | | — | | 密码哈希 |
| `role` | `enum('admin','super_admin')` | NOT NULL | | `'admin'` | | 权限级别 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 账号创建时间 |

---

## 3. 表 `planning_session`（AI 规划会话）

一次「粗略路线 + 多轮对话完善」的规划过程。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 会话主键 |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属用户 → `users.id` |
| `title` | `varchar(255)` | **NULL** | | NULL | | 会话标题，如「新加坡 5 日游」 |
| `initial_brief` | `text` | **NULL** | | NULL | | 用户给出的**粗略路线/需求**（Agent 起始上下文） |
| `status` | `varchar(32)` | NOT NULL | | `'ACTIVE'` | 见下方枚举 | 会话阶段 |
| `confirmed_trip_id` | `bigint unsigned` | **NULL** | **FK / MUL** | NULL | | 确认后关联的正式行程 → `trip.id`；未确认为空 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 创建时间 |
| `updated_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 更新时自动变 | 最近修改时间 |

**`status` 取值**

| 值 | 含义 |
|---|---|
| `ACTIVE` | 对话进行中 |
| `DRAFT_READY` | 已有可确认的草稿方案 |
| `CONFIRMED` | 已生成正式 `trip`，`confirmed_trip_id` 已填写 |
| `CANCELLED` | 用户废弃该会话 |

**外键**  
- `user_id` → `users.id`  
- `confirmed_trip_id` → `trip.id`（可空；确认后写入）

**外键（本表被引用）**  
- `chat_message.session_id`、`draft_place.session_id`、`draft_activity.session_id`

---

## 4. 表 `chat_message`（会话消息）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 消息主键 |
| `session_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属会话 → `planning_session.id` |
| `role` | `varchar(32)` | NOT NULL | | — | | `user` / `assistant` / `system` |
| `content` | `mediumtext` | NOT NULL | | — | | 消息正文 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 发送时间（排序用） |

**外键**  
- `session_id` → `planning_session.id`（会话删除则级联删消息）

---

## 5. 表 `draft_place`（草稿地点）

确认前的地点草稿；可编辑、删除；地图校验后可关联正式 `destination`。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 草稿地点主键 |
| `session_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属会话 |
| `name` | `varchar(255)` | NOT NULL | | — | | 地点名称 |
| `address` | `varchar(255)` | **NULL** | | NULL | | 地址文案 |
| `latitude` | `decimal(10,7)` | **NULL** | | NULL | | 纬度；校验前可空 |
| `longitude` | `decimal(10,7)` | **NULL** | | NULL | | 经度；校验前可空 |
| `category` | `varchar(64)` | **NULL** | | NULL | | 分类：景点 / 餐厅等 |
| `validation_status` | `varchar(32)` | NOT NULL | | `'UNVALIDATED'` | 见下方 | 地图校验状态 |
| `destination_id` | `bigint unsigned` | **NULL** | **FK / MUL** | NULL | | 校验通过后关联 → `destination.id` |
| `note` | `varchar(255)` | **NULL** | | NULL | | 备注 |

**`validation_status` 取值**：`UNVALIDATED` / `VALID` / `AMBIGUOUS` / `INVALID`

**外键**  
- `session_id` → `planning_session.id`  
- `destination_id` → `destination.id`（可空，`ON DELETE SET NULL`）

---

## 6. 表 `draft_activity`（草稿活动）

规划中的活动安排；确认后映射到正式 `trip_day` / `trip_schedule`。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 草稿活动主键 |
| `session_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属会话 |
| `draft_place_id` | `bigint unsigned` | **NULL** | **FK / MUL** | NULL | | 关联草稿地点；纯活动可空 |
| `title` | `varchar(255)` | NOT NULL | | — | | 活动名，如参观 / 午餐 |
| `suggested_day` | `int unsigned` | **NULL** | | NULL | | 建议第几天（对应日后 `day_sequence`） |
| `start_time` | `time` | **NULL** | | NULL | | 建议开始时间 |
| `end_time` | `time` | **NULL** | | NULL | | 建议结束时间 |
| `duration_minutes` | `int unsigned` | **NULL** | | NULL | | 建议停留分钟数 |

**外键**  
- `session_id` → `planning_session.id`  
- `draft_place_id` → `draft_place.id`（可空）

---

## 7. 表 `trip`（正式旅行计划）

用户确认规划后生成的正式行程主表。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 行程主键 |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属用户 → `users.id` |
| `trip_name` | `varchar(255)` | NOT NULL | | — | | 行程名称 |
| `start_date` | `date` | NOT NULL | | — | | **出发日**；推迟整趟只改这个 |
| `duration_days` | `int unsigned` | NOT NULL | | — | 约束 ≥1 | 总天数 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 记录创建时间（≠ 出发日） |
| `updated_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 更新时自动变 | 最近修改时间 |

**外键**  
- `user_id` → `users.id`  

**被引用**  
- `planning_session.confirmed_trip_id` → `trip.id`  
- `trip_preference` / `trip_day` 等

---

## 8. 表 `trip_preference`（行程偏好）

与 `trip` **一对一**。推荐附近/相似地点时读取**当前行程**的偏好（不使用用户全局偏好表）。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 主键 |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / UQ** | — | | 对应行程，唯一 |
| `travel_style` | `varchar(64)` | **NULL** | | NULL | | 旅行风格，如 culture / city / food |
| `prefer_transport` | `varchar(64)` | **NULL** | | NULL | | 偏好交通，如 walk_taxi |

**外键**  
- `trip_id` → `trip.id`

---

## 9. 表 `trip_day`（第几天）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 天主键 |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属行程 |
| `day_sequence` | `int unsigned` | NOT NULL | （与 trip_id 联合唯一） | — | 约束 ≥1 | 第几天：1、2、3… |

**说明**  
- **没有 `date` 列**；日历日看视图 `v_trip_day_calendar`。  
- 中间插天：后移后续 `day_sequence` → 再插入 → `trip.duration_days + 1`。

**外键**  
- `trip_id` → `trip.id`

---

## 10. 表 `trip_schedule`（当天停点）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 停点主键 |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 属于哪一天 |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 地点 → `destination.id` |
| `sequence` | `int unsigned` | NOT NULL | （天内唯一） | — | 约束 ≥1 | 当天顺序 |
| `start_time` | `time` | **NULL** | | NULL | | 计划开始时间 |
| `end_time` | `time` | **NULL** | | NULL | | 计划结束时间 |
| `planned_duration_minutes` | `int unsigned` | **NULL** | | NULL | | 计划停留分钟数 |
| `is_locked` | `tinyint(1)` | NOT NULL | | `0` | | `0` 未锁 / `1` 锁定（AI 少动） |
| `note` | `varchar(255)` | **NULL** | | NULL | | 备注 |

**外键**  
- `trip_day_id` → `trip_day.id`  
- `destination_id` → `destination.id`

---

## 11. 表 `trip_transport`（停点间交通）

一条记录 = 从「上一停点」到「下一停点」怎么走（路线距离/耗时；对应产品中的路线段能力）。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 主键 |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 属于哪一天 |
| `prev_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 起点停点 → `trip_schedule.id` |
| `next_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 终点停点 → `trip_schedule.id` |
| `transport_type` | `varchar(64)` | NOT NULL | | — | | Walk / Taxi / MRT… |
| `route_desc` | `text` | **NULL** | | NULL | | 路线文字描述 |
| `google_map_link` | `varchar(512)` | **NULL** | | NULL | | 外部导航链接 |
| `duration_minutes` | `int unsigned` | **NULL** | | NULL | | 交通耗时（分钟） |
| `distance_km` | `decimal(8,2)` | **NULL** | | NULL | | 距离（公里） |

**外键**  
- `trip_day_id` → `trip_day.id`  
- `prev_schedule_id` / `next_schedule_id` → `trip_schedule.id`  

**注意**：`prev_schedule_id` 与 `next_schedule_id` 不应相同（应用层保证）。

---

## 12. 表 `destination`（地点主数据）

可被多个行程的 `trip_schedule` 与草稿 `draft_place` 复用。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 地点主键 |
| `name` | `varchar(255)` | NOT NULL | MUL | — | | 地点名称 |
| `latitude` | `decimal(10,7)` | NOT NULL | | — | | 纬度 |
| `longitude` | `decimal(10,7)` | NOT NULL | | — | | 经度 |
| `category` | `varchar(64)` | **NULL** | | NULL | | 分类 |
| `opening_hours` | `varchar(255)` | **NULL** | | NULL | | 开放时间文案 |
| `description` | `text` | **NULL** | | NULL | | 简介 |
| `price` | `varchar(64)` | **NULL** | | NULL | | 价格档或门票 |
| `external_place_id` | `varchar(128)` | **NULL** | MUL | NULL | | 外部地图 Place ID |
| `address` | `varchar(255)` | NOT NULL | | — | | 地址，如 “18 Marina Gardens Drive” |

---

## 13. 表 `comment`（评论）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 评论主键 |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 评哪一处 |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 谁写的 |
| `content` | `text` | NOT NULL | | — | | 评论正文 |
| `rating` | `decimal(2,1)` | NOT NULL | | — | 约束 0–5 | 评分 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 发表时间 |

**外键**  
- `destination_id` → `destination.id`  
- `user_id` → `users.id`

---

## 视图 `v_trip_day_calendar`

```sql
SELECT day_sequence, calendar_date, trip_end_date
FROM v_trip_day_calendar
WHERE trip_id = 1
ORDER BY day_sequence;
```

---

## 常用操作与涉及字段

| 操作 | 改哪些字段/表 |
|---|---|
| 开始 AI 规划（给粗略路线） | `INSERT planning_session`（写 `initial_brief`，`status=ACTIVE`） |
| 用户/AI 发消息 | `INSERT chat_message` |
| AI 更新草稿地点/活动 | `INSERT/UPDATE/DELETE draft_place`、`draft_activity` |
| 地图校验草稿地点 | 更新 `draft_place.validation_status`，可选写入/关联 `destination` |
| 确认生成正式行程 | `INSERT trip` + `trip_day` + `trip_schedule`（+ `trip_preference` / `trip_transport`）→ 回写 `planning_session.confirmed_trip_id`，`status=CONFIRMED` |
| 按行程做推荐 | 读当前 `trip_preference` + 行程内 `destination` |
| 整趟推迟 3 天 | 只改 `trip.start_date` |
| 末尾加一天 | `INSERT trip_day` + `trip.duration_days + 1` |
| 某天加景点 | `INSERT trip_schedule`（可再插 `trip_transport`） |
| 写评论 | `INSERT comment` |

---

## 文件位置

- 本文档：`backend/DATA_DICTIONARY_zh.md`  
- Flyway 迁移：`backend/src/main/resources/db/migration/`（若表结构变更需新增 `V2__...sql`，与本文档对齐）  
- ER 图：以组内最新定稿为准（规划域 + 正式行程域）

组员在 Navicat 中也可：右键表 → **设计表** / 或执行 `SHOW CREATE TABLE trip;` 查看定义。
