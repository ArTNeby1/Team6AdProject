-- First date explicitly extracted from the user's import text. It is copied to trip.start_date
-- on confirmation so mobile and web show the date the traveler actually entered.
ALTER TABLE planning_session ADD COLUMN start_date DATE NULL AFTER duration_days;
