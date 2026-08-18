-------------------------------------------------
-- V43 — make the Project workspace able to answer questions from real data.
--
-- 1. audit_logs gains project_id, so a project can show its own timeline instead of sending the
--    user to the global audit log to hunt. Nullable and unbackfilled: rows written before this
--    migration genuinely don't know which project they concerned, and inventing one would be worse
--    than leaving it blank.
-- 2. Indexes on the project_id columns that the workspace now filters on. They were added as bare
--    columns (V17, V25, V31, V36) and, apart from Vyapar, never indexed — every project tab would
--    otherwise sequential-scan the whole table.
-------------------------------------------------

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS project_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_audit_logs_project ON audit_logs (project_id);

-- The workspace's Attendance/Staff tabs filter by (project, date range).
CREATE INDEX IF NOT EXISTS idx_payroll_attendance_project_date
    ON payroll_attendance (project_id, date);

-- Tender tab: the tender(s) a project was handed off from.
CREATE INDEX IF NOT EXISTS idx_tenders_project ON tenders (project_id);

-- Task tab and the workload rollup on the dashboard.
CREATE INDEX IF NOT EXISTS idx_tasks_project ON tasks (project_id);

-- Site geofences drawn per project (V31 added the column without one).
CREATE INDEX IF NOT EXISTS idx_payroll_locations_project ON payroll_locations (project_id);
