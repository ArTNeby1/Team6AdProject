-- 地点是不是第几天去，本该是 draft_place 级别的属性，不该依赖它有没有 activity——
-- AI 抽取经常给空 activities，之前 suggested_day/start_time 只挂在 draft_activity 上，
-- 导致这类地点在前端拖拽/自动分天时根本没有地方可以写，永远兜底成第 1 天。
ALTER TABLE draft_place
    ADD COLUMN suggested_day INT      NULL AFTER note,
    ADD COLUMN start_time    TIME     NULL AFTER suggested_day;
