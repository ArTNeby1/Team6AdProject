ALTER TABLE planning_session
    ADD COLUMN failure_reason VARCHAR(255) NULL AFTER duration_days;

CREATE TABLE user_notification (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    type                VARCHAR(32)     NOT NULL,
    title               VARCHAR(255)    NOT NULL,
    body                VARCHAR(512)    NOT NULL,
    planning_session_id BIGINT UNSIGNED NULL,
    trip_id             BIGINT UNSIGNED NULL,
    read_at             TIMESTAMP       NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_user_created (user_id, created_at),
    KEY idx_notification_user_read (user_id, read_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_session FOREIGN KEY (planning_session_id) REFERENCES planning_session (id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
