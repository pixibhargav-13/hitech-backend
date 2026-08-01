-------------------------------------------------
-- V33 — completion approval workflow. When a user who reports to someone marks a task Completed, it
-- becomes AWAITING_APPROVAL and their manager (per the role ladder; project-scoped for Team Member ->
-- Project Manager) approves or rejects it. These columns hold the in-flight request.
-------------------------------------------------

ALTER TABLE tasks
    ADD COLUMN completion_requested_by     BIGINT,
    ADD COLUMN completion_approver_role_id BIGINT,
    ADD COLUMN completion_prev_status      VARCHAR(20),
    ADD COLUMN completion_note             TEXT;
