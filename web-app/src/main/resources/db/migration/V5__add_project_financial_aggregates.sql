-------------------------------------------------
-- Project running aggregates surfaced on the frontend Project model:
--   in_amount  = money received against the project
--   out_amount = money spent on the project
--   todo_count = open action items
-- These mirror ProjectEntity (ddl-auto: validate). Idempotent so it is safe to (re)apply.
-------------------------------------------------
ALTER TABLE projects ADD COLUMN IF NOT EXISTS in_amount  DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS out_amount DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS todo_count INT              NOT NULL DEFAULT 0;

-- Seed plausible running values for the active / recently-billed projects.
UPDATE projects SET in_amount = 1500000, out_amount = 2100000, todo_count = 2 WHERE project_code = 'HT-2026-002';
UPDATE projects SET in_amount = 0,       out_amount = 1250000, todo_count = 3 WHERE project_code = 'HT-2026-003';
UPDATE projects SET in_amount = 0,       out_amount = 980000,  todo_count = 1 WHERE project_code = 'HT-2026-005';
UPDATE projects SET in_amount = 260000,  out_amount = 810000,  todo_count = 1 WHERE project_code = 'HT-2026-006';
UPDATE projects SET in_amount = 9300000, out_amount = 7200000, todo_count = 0 WHERE project_code = 'HT-2026-011';
UPDATE projects SET in_amount = 0,       out_amount = 1000000, todo_count = 1 WHERE project_code = 'HT-2026-012';
UPDATE projects SET in_amount = 4350000, out_amount = 3100000, todo_count = 2 WHERE project_code = 'HT-2026-013';
UPDATE projects SET in_amount = 3200000, out_amount = 2800000, todo_count = 1 WHERE project_code = 'HT-2026-014';
