-------------------------------------------------
-- V31 — let a work-site geofence be linked to a project. When set, EVERY member of that project can
-- punch at the site (reusing project membership), on top of any directly-assigned staff. Lets an
-- admin draw one boundary per project instead of ticking each person.
-------------------------------------------------

ALTER TABLE payroll_locations
    ADD COLUMN project_id BIGINT;
