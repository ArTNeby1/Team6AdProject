ALTER TABLE planning_session
    ADD COLUMN failure_code VARCHAR(32) NULL AFTER failure_reason;
