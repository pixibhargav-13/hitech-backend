-------------------------------------------------
-- Profile photo for members.
--
-- A member's avatar, shown on the org chart and member directory. Stored inline as a data URL
-- (downscaled client-side to ~256px, so it stays small) rather than in separate file storage.
-- Nullable, so every existing user is untouched until a photo is uploaded.
-------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS photo_url TEXT;
