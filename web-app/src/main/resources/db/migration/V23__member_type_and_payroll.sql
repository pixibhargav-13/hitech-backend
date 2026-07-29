-------------------------------------------------
-- Member classification for the unified Member model.
--
-- Every ERP member (app_user) is now classified as OFFICE (not tied to a project) or SITE
-- (works on project sites), and carries an on-payroll flag. On-payroll members can punch and
-- get a payroll profile; the flag lets one "add member" flow replace the old separate "add staff".
-- Both are nullable/defaulted so existing users are untouched until classified.
-------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS staff_type VARCHAR(10);

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS on_payroll BOOLEAN NOT NULL DEFAULT FALSE;
