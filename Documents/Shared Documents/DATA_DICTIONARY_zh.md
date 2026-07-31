# Yántú 数据库详细字段说明（组员版）

> 数据库名：**`yantu`**  
> 以下类型、可否为空、默认值、主键/外键以 **当前 MySQL 实库** 为准（可用 `DESCRIBE 表名;` 核对）。  
> 连接：`127.0.0.1:3306`，库名 `yantu`。

---

## 总览

| 对象 | 类型 | 说明 |
|---|---|---|
| `users` | 表 | App 旅行者账号 |
| `admin` | 表 | 后台管理员账号（独立登录） |
| `trip` | 表 | 一次旅行计划 |
| `trip_preference` | 表 | 行程偏好（与 trip 一对一） |
| `trip_day` | 表 | 行程中的「第几天」 |
| `trip_schedule` | 表 | 某一天内的停点 |
| `trip_transport` | 表 | 两个停点之间的交通 |
| `destination` | 表 | 地点/景点主数据 |
| `comment` | 表 | 地点评论与评分 |
| `v_trip_day_calendar` | **视图** | 由 start_date + day_sequence 算出日历日 |

**关系简图**

```text
users ──< trip ───1:1── trip_preference
  │         │
  │         └──< trip_day ──< trip_schedule >── destination
  │                    │              │
  │                    └──< trip_transport（prev/next → trip_schedule）
  └──< comment >── destination

admin（无外键，仅后台登录）
```

**日期规则（重要）**  
- 不存「每一天的 date」  
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
| `text` | 长文本 |
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
| `role` | `enum('traveler','admin')` | NOT NULL | | `'traveler'` | | 历史字段；**后台请用 `admin` 表**，新逻辑不要靠这个区分管理员 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 注册/建号时间（审计用） |

**外键（本表被引用）**  
- `trip.user_id` → `users.id`  
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
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 账号创建时间（审计用，可删） |

---

## 3. 表 `trip`（旅行计划）

一次完整行程的主表。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 行程主键 |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属用户 → `users.id` |
| `trip_name` | `varchar(255)` | NOT NULL | | — | | 行程名称 |
| `start_date` | `date` | NOT NULL | | — | | **出发日**；推迟整趟行程只改这个 |
| `duration_days` | `int unsigned` | NOT NULL | | — | 约束 ≥1 | 总天数；加一天时 +1 |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 记录创建时间（≠ 出发日） |
| `updated_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 更新时自动变 | 最近修改时间 |

**外键**  
- `fk_trip_user`：`user_id` → `users.id`（用户删除则级联删其行程）

---

## 4. 表 `trip_preference`（行程偏好）

与 `trip` **一对一**（`trip_id` 唯一）。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 主键 |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / UQ** | — | | 对应行程，唯一 |
| `travel_style` | `varchar(64)` | **NULL** | | NULL | | 旅行风格，如 culture / city |
| `prefer_transport` | `varchar(64)` | **NULL** | | NULL | | 偏好交通，如 walk_taxi |

**外键**  
- `fk_trip_preference_trip`：`trip_id` → `trip.id`

---

## 5. 表 `trip_day`（第几天）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 天主键；日程挂在此 ID |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 所属行程 → `trip.id` |
| `day_sequence` | `int unsigned` | NOT NULL | （与 trip_id 联合唯一） | — | 约束 ≥1 | 第几天：1、2、3… |

**说明**  
- **没有 `date` 列**；日历日看视图。  
- 中间插天：先把后面的 `day_sequence` 加大，再插入，并给 `trip.duration_days` +1。

**外键**  
- `fk_trip_day_trip`：`trip_id` → `trip.id`

---

## 6. 表 `trip_schedule`（当天停点）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 停点记录主键 |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 属于哪一天 → `trip_day.id` |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 去哪个地点 → `destination.id` |
| `sequence` | `int unsigned` | NOT NULL | （天内唯一） | — | 约束 ≥1 | 当天顺序（1、2、3…） |
| `start_time` | `time` | **NULL** | | NULL | | 计划开始时间，如 `09:00:00` |
| `end_time` | `time` | **NULL** | | NULL | | 计划结束时间 |
| `planned_duration_minutes` | `int unsigned` | **NULL** | | NULL | | 计划停留分钟数，如 90 |
| `is_locked` | `tinyint(1)` | NOT NULL | | `0` | | `0` 未锁 / `1` 锁定（AI 少动） |
| `note` | `varchar(255)` | **NULL** | | NULL | | 备注，如 Lunch |

**外键**  
- `fk_schedule_day`：`trip_day_id` → `trip_day.id`  
- `fk_schedule_destination`：`destination_id` → `destination.id`

---

## 7. 表 `trip_transport`（停点间交通）

一条记录 = 从「上一停点」到「下一停点」怎么走。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 主键 |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 属于哪一天 |
| `prev_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 起点停点 → `trip_schedule.id` |
| `next_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 终点停点 → `trip_schedule.id` |
| `transport_type` | `varchar(64)` | NOT NULL | | — | | 交通方式：Walk / Taxi / Boat… |
| `route_desc` | `text` | **NULL** | | NULL | | 路线文字描述 |
| `google_map_link` | `varchar(512)` | **NULL** | | NULL | | Google Maps 链接 |
| `duration_minutes` | `int unsigned` | **NULL** | | NULL | | 交通耗时（分钟） |
| `distance_km` | `decimal(8,2)` | **NULL** | | NULL | | 距离（公里），最多两位小数 |

**外键**  
- `fk_transport_day`：`trip_day_id` → `trip_day.id`  
- `fk_transport_prev`：`prev_schedule_id` → `trip_schedule.id`  
- `fk_transport_next`：`next_schedule_id` → `trip_schedule.id`  

**注意**：`prev_schedule_id` 与 `next_schedule_id` 不应相同（应用层保证）。

---

## 8. 表 `destination`（地点）

可被多个行程的 `trip_schedule` 复用。

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 地点主键 |
| `name` | `varchar(255)` | NOT NULL | MUL（索引） | — | | 地点名称 |
| `latitude` | `decimal(10,7)` | NOT NULL | | — | | 纬度 |
| `longitude` | `decimal(10,7)` | NOT NULL | | — | | 经度 |
| `category` | `varchar(64)` | **NULL** | | NULL | | 分类：Temple / Market… |
| `opening_hours` | `varchar(255)` | **NULL** | | NULL | | 开放时间文案 |
| `description` | `text` | **NULL** | | NULL | | 简介 |
| `price` | `varchar(64)` | **NULL** | | NULL | | 价格档或门票，如 Free / 60 THB |
| `external_place_id` | `varchar(128)` | **NULL** | MUL（索引） | NULL | | 外部地图 Place ID |

---

## 9. 表 `comment`（评论）

| 字段名 | 数据类型 | 空 | 键 | 默认值 | 其它 | 中文说明 |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | 评论主键 |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 评哪一处 → `destination.id` |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | 谁写的 → `users.id` |
| `content` | `text` | NOT NULL | | — | | 评论正文 |
| `rating` | `decimal(2,1)` | NOT NULL | | — | 约束 0–5 | 评分，如 `4.5`、`5.0` |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | 自动生成 | 发表时间 |

**外键**  
- `fk_comment_destination`：`destination_id` → `destination.id`  
- `fk_comment_user`：`user_id` → `users.id`


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
| 整趟推迟 3 天 | 只改 `trip.start_date` |
| 末尾加一天 | `INSERT trip_day` + `trip.duration_days + 1` |
| 中间插一天 | 后移 `trip_day.day_sequence` → 再插入 → `duration_days + 1` |
| 某天加景点 | `INSERT trip_schedule`（可再插 `trip_transport`） |
| 写评论 | `INSERT comment` |

---

## 文件位置

- 本文档：`docs/DATA_DICTIONARY.md`  
- 建表 DDL：`sql/01_schema_mysql.sql`  

组员在 Navicat 中也可：右键表 → **设计表** / 或执行 `SHOW CREATE TABLE trip;` 查看同款定义。
