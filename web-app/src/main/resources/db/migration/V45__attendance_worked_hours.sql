-------------------------------------------------
-- V45 — make the shift definition actually mean something.
--
-- halfDayHours / fullDayHours / graceMinutes / overtimeEnabled were written by the Shift settings
-- screen and read by nothing: a punch of any length set code='P', overtime was only ever typed in
-- by an admin, and payroll divided OT by a hardcoded 8 regardless of the shift. A 3-hour day on a
-- 6-hour shift therefore paid a full day, and a 9-hour day paid no overtime.
--
-- worked_hours stores what the punch pair actually came to, so the derivation is auditable and
-- payroll doesn't have to re-parse "07:00"/"13:00" strings. NULL means the pair is incomplete
-- (punched in, never out) — a real and very common site case that must not silently pay a full day.
-------------------------------------------------

ALTER TABLE payroll_attendance
    ADD COLUMN IF NOT EXISTS worked_hours NUMERIC(5, 2);

COMMENT ON COLUMN payroll_attendance.worked_hours IS
    'Hours between punch in and out, net of grace. NULL = incomplete punch pair.';
