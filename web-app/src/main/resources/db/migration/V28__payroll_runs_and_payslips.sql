-------------------------------------------------
-- Payroll runs — one per calendar month — and the per-member payslips generated inside them.
--
-- Generating a run reads every on-payroll member's attendance for the month, their salary profile,
-- their outstanding loans and their approved reimbursements, and computes gross/PF/ESIC/PT/EMI/net.
-- Runs start as DRAFT (regeneratable, editable), get LOCKED once reviewed, then mark PAID once
-- payments are recorded in payroll_payments (Phase 6).
--
-- Formula matches the client-side computePayslip() that's been proven for months in the preview.
-------------------------------------------------

CREATE TABLE payroll_runs
(
    id         BIGSERIAL PRIMARY KEY,
    month      VARCHAR(7)  NOT NULL UNIQUE,   -- yyyy-MM
    status     VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    total_net  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_gross NUMERIC(14, 2) NOT NULL DEFAULT 0,
    person_count INT       NOT NULL DEFAULT 0,
    locked_by  BIGINT REFERENCES app_users (id) ON DELETE SET NULL,
    locked_at  TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_run_status CHECK (status IN ('DRAFT', 'LOCKED', 'PAID'))
);

CREATE TABLE payroll_payslips
(
    id             BIGSERIAL PRIMARY KEY,
    run_id         BIGINT      NOT NULL REFERENCES payroll_runs (id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    gross          NUMERIC(12, 2) NOT NULL DEFAULT 0,
    pf             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    esic           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    pt             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    loan_emi       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reimbursements NUMERIC(12, 2) NOT NULL DEFAULT 0,
    net            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payable_days   NUMERIC(4, 1) NOT NULL DEFAULT 0,
    total_days     INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payslip_run_user UNIQUE (run_id, user_id)
);
CREATE INDEX idx_payroll_payslips_user_month ON payroll_payslips (user_id, run_id);
