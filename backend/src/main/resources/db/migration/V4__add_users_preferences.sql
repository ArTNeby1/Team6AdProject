ALTER TABLE users
    ADD COLUMN travel_style     VARCHAR(64) NULL AFTER gender,
    ADD COLUMN prefer_transport VARCHAR(64) NULL AFTER travel_style;
