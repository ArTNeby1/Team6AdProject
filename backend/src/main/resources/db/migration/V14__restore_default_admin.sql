-- Restore the default super_admin removed by V13.
--
-- V13 (from PR #71) deleted 'admin@loomytrip.local'. PR #71 was later reverted,
-- but V13 had already run against the live database, so the account stayed gone
-- and admin login broke. Flyway will not re-run V2, so this new migration
-- re-inserts the account.
--
-- NOTE: this restores the original committed password ("Admin@12345"). Rotate it
-- immediately via the admin console after logging in.
--
-- Idempotent: does nothing if an admin with this email already exists.

INSERT INTO admin (email, password_hash, role)
SELECT 'admin@loomytrip.local',
       '$2b$10$3T2K4thrJPIv2SC11YO7neFpyShBFClGKFRa8BRYnpTolPIkHyMLW',
       'super_admin'
WHERE NOT EXISTS (
    SELECT 1 FROM admin WHERE email = 'admin@loomytrip.local'
);
