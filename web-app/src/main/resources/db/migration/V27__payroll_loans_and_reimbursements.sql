-------------------------------------------------
-- Loans (advances given, repaid in EMIs against salary) and Reimbursements (expenses claimed by
-- members and paid back). Both are keyed by member (user_id), never by any legacy Employee id.
--
-- Loans compute EMI client-side on the form (flat / simple / compound); we store the computed EMI
-- and outstanding so payroll runs can deduct EMIs from net without recomputing.
-------------------------------------------------

CREATE TABLE payroll_loans
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    principal         NUMERIC(12, 2) NOT NULL,
    tenure_months     INT          NOT NULL,
    annual_rate       NUMERIC(5, 2) NOT NULL DEFAULT 0,
    interest_type     VARCHAR(15)  NOT NULL DEFAULT 'FLAT',
    disbursement_date DATE         NOT NULL,
    start_month       VARCHAR(7)   NOT NULL,
    emi               NUMERIC(12, 2) NOT NULL,
    outstanding       NUMERIC(12, 2) NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_loan_interest_type CHECK (interest_type IN ('FLAT', 'SIMPLE', 'COMPOUND'))
);
CREATE INDEX idx_payroll_loans_user ON payroll_loans (user_id);

CREATE TABLE payroll_reimbursements
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expense_type     VARCHAR(120) NOT NULL,
    claim_id         VARCHAR(30)  NOT NULL,
    expense_date     DATE         NOT NULL,
    applied_at       DATE         NOT NULL,
    approved_at      DATE,
    settlement_date  DATE,
    requested_amount NUMERIC(12, 2) NOT NULL,
    approved_amount  NUMERIC(12, 2),
    approver_id      BIGINT REFERENCES app_users (id) ON DELETE SET NULL,
    status           VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_reimb_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID'))
);
CREATE INDEX idx_payroll_reimb_user ON payroll_reimbursements (user_id, status);
CREATE INDEX idx_payroll_reimb_status ON payroll_reimbursements (status, applied_at DESC);
