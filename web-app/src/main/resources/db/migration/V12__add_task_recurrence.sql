-------------------------------------------------
-- Recurring tasks. A task carries its own repeat rule; when it is completed the service spawns
-- the next occurrence and links it back to the same series, so a series can be reported on.
-------------------------------------------------
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- Repeat every N days/weeks/months (1 = every day/week/month).
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS recurrence_interval INTEGER NOT NULL DEFAULT 1;

-- Optional end of the series (yyyy-MM-dd); NULL = repeat indefinitely.
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS recurrence_until VARCHAR(30);

-- All occurrences of one repeating task share a series id (the id of the first task).
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS series_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_tasks_series ON tasks (series_id);
CREATE INDEX IF NOT EXISTS idx_tasks_recurrence ON tasks (recurrence_rule);
