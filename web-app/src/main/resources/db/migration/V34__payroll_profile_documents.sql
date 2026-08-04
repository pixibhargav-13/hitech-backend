-------------------------------------------------
-- A member's identity documents (Aadhaar, PAN and any extras) stored as a JSON array of
-- {type, number} objects. Nullable so existing profiles stay valid under ddl-auto: validate.
-------------------------------------------------

ALTER TABLE payroll_profiles ADD COLUMN IF NOT EXISTS documents TEXT;
