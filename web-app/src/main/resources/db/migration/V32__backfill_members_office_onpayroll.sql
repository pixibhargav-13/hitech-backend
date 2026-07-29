-------------------------------------------------
-- V32 — one-time production backfill. Every member already added is office staff on payroll (that's
-- how they were entered), so classify them all in one go: on_payroll = true, staff_type = 'OFFICE'.
-- This keeps Taskopad visibility correct (office = sees all tasks) and gives everyone a payroll
-- profile slot without re-editing each person. New members are classified at creation.
-------------------------------------------------

UPDATE app_users
SET on_payroll = TRUE,
    staff_type = 'OFFICE';
