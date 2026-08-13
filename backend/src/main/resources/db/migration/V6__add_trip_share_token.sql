ALTER TABLE trip ADD COLUMN share_token VARCHAR(64) NULL AFTER is_favorite;
ALTER TABLE trip ADD CONSTRAINT uq_trip_share_token UNIQUE (share_token);
