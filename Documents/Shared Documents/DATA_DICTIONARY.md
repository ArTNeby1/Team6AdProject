# Yántú Database — Detailed Data Dictionary

> **Database name:** `yantu`  
> Column types, nullability, defaults, and keys below match the **current MySQL database** (verify with `DESCRIBE table_name;`).  
> Connection: `127.0.0.1:3306`, database `yantu`.

---

## Overview

| Object | Kind | Description |
|---|---|---|
| `users` | Table | Traveler accounts (mobile app) |
| `admin` | Table | Admin Console accounts (separate login) |
| `trip` | Table | A travel plan / itinerary |
| `trip_preference` | Table | Trip preferences (1:1 with `trip`) |
| `trip_day` | Table | One day within a trip (“Day N”) |
| `trip_schedule` | Table | A stop / activity within a day |
| `trip_transport` | Table | Transport between two consecutive stops |
| `destination` | Table | Place / attraction master data |
| `comment` | Table | Reviews and ratings for a place |
| `v_trip_day_calendar` | **View** | Derives calendar dates from `start_date` + `day_sequence` |

**Relationship sketch**

```text
users ──< trip ───1:1── trip_preference
  │         │
  │         └──< trip_day ──< trip_schedule >── destination
  │                    │              │
  │                    └──< trip_transport (prev/next → trip_schedule)
  └──< comment >── destination

admin (no FKs; Admin Console login only)
```

**Date rules (important)**  
- Do **not** store a per-day `date` column  
- `calendar_date = start_date + (day_sequence - 1)`  
- `trip_end_date = start_date + (duration_days - 1)`  
- Query calendar days via the view `v_trip_day_calendar`

---

## MySQL type cheat sheet

| MySQL type | Meaning |
|---|---|
| `bigint unsigned` | Unsigned large integer; typical auto-increment PK / FK |
| `int unsigned` | Unsigned integer (days, minutes, sequence numbers, etc.) |
| `tinyint(1)` | Commonly used as boolean: `0` / `1` |
| `decimal(M,D)` | Fixed-point number; e.g. `decimal(10,7)` for coordinates, `decimal(2,1)` for ratings |
| `varchar(N)` | Variable-length string, max N characters |
| `text` | Long text |
| `date` | Date, format `YYYY-MM-DD` |
| `time` | Time of day, format `HH:MM:SS` |
| `timestamp` | Date-time; can default to current time |
| `enum(...)` | Restricted to the listed values only |

Abbreviations used in column tables:

| Mark | Meaning |
|---|---|
| **PK** | Primary Key |
| **FK** | Foreign Key |
| **UQ** | Unique |
| **AI** | Auto-increment (`AUTO_INCREMENT`) |
| **NULL** | Nullable |
| **NOT NULL** | Required |

---

## 1. Table `users` (travelers)

Mobile app login and profile.

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | User primary key |
| `email` | `varchar(255)` | NOT NULL | **UQ** | — | | Login email; globally unique |
| `password_hash` | `varchar(255)` | NOT NULL | | — | | Password hash (never store plaintext) |
| `age` | `int unsigned` | **NULL** | | NULL | | Age (optional) |
| `gender` | `varchar(32)` | **NULL** | | NULL | | Gender (optional) |
| `role` | `enum('traveler','admin')` | NOT NULL | | `'traveler'` | | Legacy field; **use the `admin` table for back-office staff** — do not rely on this for admin auth |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | auto | Account creation time (audit; optional) |

**Referenced by**  
- `trip.user_id` → `users.id`  
- `comment.user_id` → `users.id`

---

## 2. Table `admin` (Admin Console operators)

Admin Console login; **no foreign keys** to `users` or other business tables.

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Admin primary key |
| `email` | `varchar(255)` | NOT NULL | **UQ** | — | | Console login email |
| `password_hash` | `varchar(255)` | NOT NULL | | — | | Password hash |
| `role` | `enum('admin','super_admin')` | NOT NULL | | `'admin'` | | Privilege level |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | auto | Account creation time (audit; can be removed) |

---

## 3. Table `trip` (travel plan)

Root entity for one itinerary.

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Trip primary key |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Owner → `users.id` |
| `trip_name` | `varchar(255)` | NOT NULL | | — | | Trip display name |
| `start_date` | `date` | NOT NULL | | — | | **Departure date**; postpone the whole trip by changing only this |
| `duration_days` | `int unsigned` | NOT NULL | | — | check ≥ 1 | Total number of days; increment when adding a day |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | auto | Row creation time (**not** the travel start date) |
| `updated_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | on update | Last modification time |

**Foreign keys**  
- `fk_trip_user`: `user_id` → `users.id` (deleting a user cascades to their trips)

---

## 4. Table `trip_preference` (trip preferences)

**One-to-one** with `trip` (`trip_id` is unique).

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Primary key |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / UQ** | — | | Linked trip (unique) |
| `travel_style` | `varchar(64)` | **NULL** | | NULL | | e.g. culture / city / relaxed |
| `prefer_transport` | `varchar(64)` | **NULL** | | NULL | | Preferred transport, e.g. walk_taxi |

**Foreign keys**  
- `fk_trip_preference_trip`: `trip_id` → `trip.id`

---

## 5. Table `trip_day` (Day N)

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Day primary key; schedules attach to this ID |
| `trip_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Parent trip → `trip.id` |
| `day_sequence` | `int unsigned` | NOT NULL | unique with `trip_id` | — | check ≥ 1 | Day index: 1, 2, 3, … |

**Notes**  
- There is **no `date` column**; use the calendar view.  
- To insert a day in the middle: shift later `day_sequence` values up, insert the new day, then set `trip.duration_days = duration_days + 1`.

**Foreign keys**  
- `fk_trip_day_trip`: `trip_id` → `trip.id`

---

## 6. Table `trip_schedule` (stops within a day)

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Schedule-item primary key |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Which day → `trip_day.id` |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Which place → `destination.id` |
| `sequence` | `int unsigned` | NOT NULL | unique within day | — | check ≥ 1 | Order within the day (1, 2, 3, …) |
| `start_time` | `time` | **NULL** | | NULL | | Planned start, e.g. `09:00:00` |
| `end_time` | `time` | **NULL** | | NULL | | Planned end |
| `planned_duration_minutes` | `int unsigned` | **NULL** | | NULL | | Planned stay length in minutes, e.g. 90 |
| `is_locked` | `tinyint(1)` | NOT NULL | | `0` | | `0` unlocked / `1` locked (AI should avoid moving it) |
| `note` | `varchar(255)` | **NULL** | | NULL | | Note, e.g. Lunch |

**Foreign keys**  
- `fk_schedule_day`: `trip_day_id` → `trip_day.id`  
- `fk_schedule_destination`: `destination_id` → `destination.id`

---

## 7. Table `trip_transport` (transport between stops)

One row = how to go from the **previous** stop to the **next** stop.

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Primary key |
| `trip_day_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Which day |
| `prev_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | From stop → `trip_schedule.id` |
| `next_schedule_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | To stop → `trip_schedule.id` |
| `transport_type` | `varchar(64)` | NOT NULL | | — | | Mode: Walk / Taxi / Boat / … |
| `route_desc` | `text` | **NULL** | | NULL | | Human-readable route description |
| `google_map_link` | `varchar(512)` | **NULL** | | NULL | | Google Maps URL |
| `duration_minutes` | `int unsigned` | **NULL** | | NULL | | Travel time in minutes |
| `distance_km` | `decimal(8,2)` | **NULL** | | NULL | | Distance in km (2 decimal places) |

**Foreign keys**  
- `fk_transport_day`: `trip_day_id` → `trip_day.id`  
- `fk_transport_prev`: `prev_schedule_id` → `trip_schedule.id`  
- `fk_transport_next`: `next_schedule_id` → `trip_schedule.id`

**Note:** `prev_schedule_id` and `next_schedule_id` must not be equal (enforce in application code).

---

## 8. Table `destination` (places)

Reusable across many trips’ schedules.

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Place primary key |
| `name` | `varchar(255)` | NOT NULL | MUL (index) | — | | Place name |
| `latitude` | `decimal(10,7)` | NOT NULL | | — | | Latitude |
| `longitude` | `decimal(10,7)` | NOT NULL | | — | | Longitude |
| `category` | `varchar(64)` | **NULL** | | NULL | | Category: Temple / Market / … |
| `opening_hours` | `varchar(255)` | **NULL** | | NULL | | Opening-hours text |
| `description` | `text` | **NULL** | | NULL | | Description |
| `price` | `varchar(64)` | **NULL** | | NULL | | Price tier or ticket, e.g. Free / 60 THB |
| `external_place_id` | `varchar(128)` | **NULL** | MUL (index) | NULL | | External map Place ID |

---

## 9. Table `comment` (reviews)

| Column | Data type | Null | Key | Default | Extra | Description |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NOT NULL | **PK** | — | **AI** | Comment primary key |
| `destination_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Place reviewed → `destination.id` |
| `user_id` | `bigint unsigned` | NOT NULL | **FK / MUL** | — | | Author → `users.id` |
| `content` | `text` | NOT NULL | | — | | Review body |
| `rating` | `decimal(2,1)` | NOT NULL | | — | check 0–5 | Score, e.g. `4.5`, `5.0` |
| `created_at` | `timestamp` | NOT NULL | | `CURRENT_TIMESTAMP` | auto | Posted time |

**Foreign keys**  
- `fk_comment_destination`: `destination_id` → `destination.id`  
- `fk_comment_user`: `user_id` → `users.id`

---

## 10. View `v_trip_day_calendar` (read-only)

Not a physical table; computed at query time.

| Column | Source / meaning | Description |
|---|---|---|
| `trip_day_id` | `trip_day.id` (`bigint`) | Day row ID |
| `trip_id` | `trip_day.trip_id` (`bigint`) | Trip ID |
| `day_sequence` | `trip_day.day_sequence` (`int`) | Day index |
| `start_date` | `trip.start_date` (`date`) | Trip departure date |
| `calendar_date` | computed: `start_date + (day_sequence - 1)` days | **Calendar date for that day** |
| `trip_end_date` | computed: `start_date + (duration_days - 1)` days | **Trip end date** |

```sql
SELECT day_sequence, calendar_date, trip_end_date
FROM v_trip_day_calendar
WHERE trip_id = 1
ORDER BY day_sequence;
```

Do **not** draw this view on the ERD unless your instructor asks for derived objects; document it in text instead.

---

## Common operations → columns to change

| Operation | What to update |
|---|---|
| Postpone whole trip by 3 days | Only `trip.start_date` |
| Append one day at the end | `INSERT trip_day` + `trip.duration_days + 1` |
| Insert a day in the middle | Shift later `trip_day.day_sequence` → insert → `duration_days + 1` |
| Add a stop on a day | `INSERT trip_schedule` (optionally `trip_transport`) |
| Post a review | `INSERT comment` |

---

## File locations

- English (this file): `docs/DATA_DICTIONARY.md`  
- Chinese copy: `docs/DATA_DICTIONARY.zh.md`  
- DDL: `sql/01_schema_mysql.sql`

In Navicat: right-click a table → **Design Table**, or run `SHOW CREATE TABLE trip;` to see the same definitions.
