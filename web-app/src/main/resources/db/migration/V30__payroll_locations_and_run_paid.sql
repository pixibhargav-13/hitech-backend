-------------------------------------------------
-- V30 — two things:
--   1. Manual payout marker on a payroll run. Hi-Tech pays salaries by hand (bank / cash), so the
--      system only RECORDS that a locked run was paid — it never moves money. paid_at / paid_by
--      capture when and by whom the run was marked disbursed.
--   2. Work-site geofences (polygons) + member assignment, moved server-side so punch-in/out can be
--      enforced to "inside an assigned site only", and locations persist across devices.
-------------------------------------------------

ALTER TABLE payroll_runs
    ADD COLUMN paid_at TIMESTAMP,
    ADD COLUMN paid_by BIGINT;

CREATE TABLE payroll_locations
(
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT      NOT NULL,
    points     TEXT      NOT NULL,   -- JSON array of {"lat":..,"lng":..} polygon vertices
    member_ids TEXT,                 -- JSON array of assigned member (user) ids
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
