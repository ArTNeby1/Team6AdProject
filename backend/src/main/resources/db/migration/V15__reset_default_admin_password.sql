-- Reset the default super_admin password to the fixed bootstrap value.
--
-- Context: while PR #71 was live, AdminSeeder recreated 'admin@loomytrip.local'
-- with a RANDOM password from Secrets Manager. PR #71 was later reverted, but
-- that row (with the random password) remained. V14 could not fix it because it
-- only INSERTs when the row is absent. This migration UPDATEs the existing row.
--
-- Sets the password back to the known bootstrap value "Admin@12345" so the app
-- can be logged into for demo/testing. NOTE: this password is publicly known --
-- rotate it before any real production use.

UPDATE admin
SET password_hash = '$2b$10$3T2K4thrJPIv2SC11YO7neFpyShBFClGKFRa8BRYnpTolPIkHyMLW'
WHERE email = 'admin@loomytrip.local';
