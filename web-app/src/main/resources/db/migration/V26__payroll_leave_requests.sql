-------------------------------------------------
-- Payroll leave requests — a member applies for leave against their assigned Leave Policy's
-- balance; someone with PAYROLL:APPROVE reviews it; approved days write PL rows into
-- payroll_attendance so the muster + payroll run pick them up automatically.
--
-- Leave-type is stored as a plain name (not a FK) because leave types are children of a policy —
-- pinning them by name keeps the request valid even if the policy is edited later.
-------------------------------------------------

CREATE TABLE payroll_leave_requests
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    leave_type_name VARCHAR(120) NOT NULL,
    from_date       DATE         NOT NULL,
    to_date         DATE         NOT NULL,
    days            NUMERIC(4, 1) NOT NULL,
    reason          TEXT,
    status          VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    approver_id     BIGINT REFERENCES app_users (id) ON DELETE SET NULL,
    approved_at     TIMESTAMP,
    decision_note   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);
CREATE INDEX idx_payroll_leave_user ON payroll_leave_requests (user_id, status);
CREATE INDEX idx_payroll_leave_status ON payroll_leave_requests (status, created_at DESC);
