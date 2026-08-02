-- LoomyTrip initial schema (table design aligned with DATA_DICTIONARY; DB name: LoomyTrip)

CREATE TABLE users (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    age             INT UNSIGNED    NULL,
    gender          VARCHAR(32)     NULL,
    role            ENUM('traveler', 'admin') NOT NULL DEFAULT 'traveler',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            ENUM('admin', 'super_admin') NOT NULL DEFAULT 'admin',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admin_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE destination (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name                VARCHAR(255)    NOT NULL,
    latitude            DECIMAL(10, 7)  NOT NULL,
    longitude           DECIMAL(10, 7)  NOT NULL,
    category            VARCHAR(64)     NULL,
    opening_hours       VARCHAR(255)    NULL,
    description         TEXT            NULL,
    price               VARCHAR(64)     NULL,
    external_place_id   VARCHAR(128)    NULL,
    address             VARCHAR(255)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_destination_name (name),
    KEY idx_destination_external_place_id (external_place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    trip_name       VARCHAR(255)    NOT NULL,
    start_date      DATE            NOT NULL,
    duration_days   INT UNSIGNED    NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_trip_user_id (user_id),
    CONSTRAINT fk_trip_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_trip_duration CHECK (duration_days >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_preference (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id             BIGINT UNSIGNED NOT NULL,
    travel_style        VARCHAR(64)     NULL,
    prefer_transport    VARCHAR(64)     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_trip_preference_trip (trip_id),
    CONSTRAINT fk_trip_preference_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_day (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_id         BIGINT UNSIGNED NOT NULL,
    day_sequence    INT UNSIGNED    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_trip_day_sequence (trip_id, day_sequence),
    KEY idx_trip_day_trip_id (trip_id),
    CONSTRAINT fk_trip_day_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT chk_trip_day_sequence CHECK (day_sequence >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_schedule (
    id                          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_day_id                 BIGINT UNSIGNED NOT NULL,
    destination_id              BIGINT UNSIGNED NOT NULL,
    sequence                    INT UNSIGNED    NOT NULL,
    start_time                  TIME            NULL,
    end_time                    TIME            NULL,
    planned_duration_minutes    INT UNSIGNED    NULL,
    is_locked                   TINYINT(1)      NOT NULL DEFAULT 0,
    note                        VARCHAR(255)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_trip_schedule_day_seq (trip_day_id, sequence),
    KEY idx_trip_schedule_day (trip_day_id),
    KEY idx_trip_schedule_destination (destination_id),
    CONSTRAINT fk_schedule_day FOREIGN KEY (trip_day_id) REFERENCES trip_day (id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_destination FOREIGN KEY (destination_id) REFERENCES destination (id),
    CONSTRAINT chk_trip_schedule_sequence CHECK (sequence >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_transport (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trip_day_id         BIGINT UNSIGNED NOT NULL,
    prev_schedule_id    BIGINT UNSIGNED NOT NULL,
    next_schedule_id    BIGINT UNSIGNED NOT NULL,
    transport_type      VARCHAR(64)     NOT NULL,
    route_desc          TEXT            NULL,
    google_map_link     VARCHAR(512)    NULL,
    duration_minutes    INT UNSIGNED    NULL,
    distance_km         DECIMAL(8, 2)   NULL,
    PRIMARY KEY (id),
    KEY idx_transport_day (trip_day_id),
    KEY idx_transport_prev (prev_schedule_id),
    KEY idx_transport_next (next_schedule_id),
    CONSTRAINT fk_transport_day FOREIGN KEY (trip_day_id) REFERENCES trip_day (id) ON DELETE CASCADE,
    CONSTRAINT fk_transport_prev FOREIGN KEY (prev_schedule_id) REFERENCES trip_schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_transport_next FOREIGN KEY (next_schedule_id) REFERENCES trip_schedule (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE comment (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    destination_id  BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    content         TEXT            NOT NULL,
    rating          DECIMAL(2, 1)   NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_destination (destination_id),
    KEY idx_comment_user (user_id),
    CONSTRAINT fk_comment_destination FOREIGN KEY (destination_id) REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_comment_rating CHECK (rating >= 0 AND rating <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_preference (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    preference_key  VARCHAR(64)     NOT NULL,
    preference_value VARCHAR(255)   NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_preference (user_id, preference_key),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE imported_source (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    source_type     VARCHAR(32)     NOT NULL,
    title           VARCHAR(255)    NULL,
    raw_content     MEDIUMTEXT      NULL,
    source_url      VARCHAR(1024)   NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    error_message   TEXT            NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_imported_source_user (user_id),
    KEY idx_imported_source_status (status),
    CONSTRAINT fk_imported_source_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE extracted_place (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id           BIGINT UNSIGNED NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    address             VARCHAR(255)    NULL,
    latitude            DECIMAL(10, 7)  NULL,
    longitude           DECIMAL(10, 7)  NULL,
    category            VARCHAR(64)     NULL,
    validation_status   VARCHAR(32)     NOT NULL DEFAULT 'UNVALIDATED',
    destination_id      BIGINT UNSIGNED NULL,
    note                VARCHAR(255)    NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_extracted_place_import (import_id),
    KEY idx_extracted_place_destination (destination_id),
    CONSTRAINT fk_extracted_place_import FOREIGN KEY (import_id) REFERENCES imported_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_extracted_place_destination FOREIGN KEY (destination_id) REFERENCES destination (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE extracted_activity (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    import_id           BIGINT UNSIGNED NOT NULL,
    extracted_place_id  BIGINT UNSIGNED NULL,
    title               VARCHAR(255)    NOT NULL,
    description         TEXT            NULL,
    suggested_day       INT UNSIGNED    NULL,
    start_time          TIME            NULL,
    end_time            TIME            NULL,
    duration_minutes    INT UNSIGNED    NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_extracted_activity_import (import_id),
    KEY idx_extracted_activity_place (extracted_place_id),
    CONSTRAINT fk_extracted_activity_import FOREIGN KEY (import_id) REFERENCES imported_source (id) ON DELETE CASCADE,
    CONSTRAINT fk_extracted_activity_place FOREIGN KEY (extracted_place_id) REFERENCES extracted_place (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE OR REPLACE VIEW v_trip_day_calendar AS
SELECT
    td.id AS trip_day_id,
    td.trip_id,
    td.day_sequence,
    DATE_ADD(t.start_date, INTERVAL (td.day_sequence - 1) DAY) AS calendar_date,
    DATE_ADD(t.start_date, INTERVAL (t.duration_days - 1) DAY) AS trip_end_date
FROM trip_day td
JOIN trip t ON t.id = td.trip_id;
