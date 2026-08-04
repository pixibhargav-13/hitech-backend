-------------------------------------------------
-- Configurable salary components. Replaces the hardcoded Basic 50% / HRA 20% split and the fixed
-- PF/ESIC/PT rates. Each profile carries its own component list (delimited text, since the
-- payroll-service has no Jackson) so it can differ per employee; a single-row template holds the
-- org-wide default new employees inherit. Payslips gain a slot for the summed custom deductions
-- plus a human-readable breakdown.
-------------------------------------------------

ALTER TABLE payroll_profiles ADD COLUMN IF NOT EXISTS components TEXT;

ALTER TABLE payroll_payslips ADD COLUMN IF NOT EXISTS other_deductions NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_payslips ADD COLUMN IF NOT EXISTS deductions_detail TEXT;

CREATE TABLE IF NOT EXISTS payroll_salary_template
(
    id         BIGSERIAL PRIMARY KEY,
    components TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
