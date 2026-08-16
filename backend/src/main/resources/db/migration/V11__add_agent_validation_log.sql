CREATE TABLE agent_validation_log (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    planning_session_id BIGINT UNSIGNED NULL,
    operation           VARCHAR(32)     NOT NULL,
    request_payload     LONGTEXT        NOT NULL,
    response_payload    LONGTEXT        NOT NULL,
    outcome             VARCHAR(16)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_validation_created (created_at),
    KEY idx_agent_validation_session (planning_session_id),
    CONSTRAINT fk_agent_validation_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_validation_session
        FOREIGN KEY (planning_session_id) REFERENCES planning_session (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
