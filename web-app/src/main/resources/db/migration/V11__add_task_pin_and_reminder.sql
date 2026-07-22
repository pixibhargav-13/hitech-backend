-------------------------------------------------
-- Taskopad: pin a task to the top of the list, and set a per-task reminder.
-------------------------------------------------
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS reminder_at VARCHAR(30);

-- Pinned tasks are read first on every list query.
CREATE INDEX IF NOT EXISTS idx_tasks_pinned ON tasks (is_pinned);
