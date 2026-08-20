-- Remove the compromised default admin seeded by V2.
--
-- V2 shipped a publicly-known password ("Admin@12345"): its BCrypt hash was
-- committed to the repo, so anyone reading the source could log in as
-- super_admin. V2 itself cannot be edited (Flyway rejects a changed checksum on
-- an already-applied migration), so this migration cleans up after it.
--
-- Delete that account ONLY if its password is still the compromised hash — i.e.
-- nobody rotated it. An admin who changed the password (different hash) is left
-- untouched. The replacement super_admin is now created at startup by
-- AdminSeeder using a random password from Secrets Manager
-- (SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD).
DELETE FROM admin
WHERE email = 'admin@loomytrip.local'
  AND password_hash = '$2b$10$3T2K4thrJPIv2SC11YO7neFpyShBFClGKFRa8BRYnpTolPIkHyMLW';
