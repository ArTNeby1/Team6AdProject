-- AI 从用户文字里识别出的"玩几天"，null 代表用户没说、前端该弹窗问。
-- 见 PlanningService#persistExtraction 里对 duration_days 字段的解析。
ALTER TABLE planning_session ADD COLUMN duration_days INT NULL AFTER status;
