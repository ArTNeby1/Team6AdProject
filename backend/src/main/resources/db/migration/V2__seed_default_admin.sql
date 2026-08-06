-- Seed a default super_admin so the admin console login works out of the box.
--
-- DEV/BOOTSTRAP ONLY. The BCrypt hash below is for the password "Admin@12345".
-- Rotate this immediately in any shared or production environment (change the
-- password via a real admin-management flow, or replace this seed).
--
-- Idempotent: INSERT ... WHERE NOT EXISTS avoids a duplicate on re-run and does
-- nothing if an admin with this email already exists.

INSERT INTO admin (email, password_hash, role)
SELECT 'admin@loomytrip.local',
       '$2b$10$3T2K4thrJPIv2SC11YO7neFpyShBFClGKFRa8BRYnpTolPIkHyMLW',
       'super_admin'
WHERE NOT EXISTS (
    SELECT 1 FROM admin WHERE email = 'admin@loomytrip.local'
);
