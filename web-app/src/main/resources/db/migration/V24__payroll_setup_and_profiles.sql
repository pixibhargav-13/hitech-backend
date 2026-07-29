-------------------------------------------------
-- Payroll setup policies (Shifts, Holiday Policy, Leave Policy) and per-member payroll profiles.
--
-- Policies are configured once in Payroll -> Setup and referenced by id from each member's
-- profile (shift_id / holiday_policy_id / leave_policy_id), so editing a policy updates everyone
-- assigned to it. A payroll profile is the "how they're paid" half of a member — identity stays
-- on app_users; this only adds employment/salary/statutory/bank + policy assignment, one row per
-- member (unique on user_id).
-------------------------------------------------

CREATE TABLE payroll_shifts
(
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(120) NOT NULL,
    start_time       VARCHAR(5)   NOT NULL,
    end_time         VARCHAR(5)   NOT NULL,
    weekly_offs      VARCHAR(30)  NOT NULL DEFAULT '',
    grace_minutes    INT          NOT NULL DEFAULT 0,
    half_day_hours   DOUBLE PRECISION NOT NULL DEFAULT 4,
    full_day_hours   DOUBLE PRECISION NOT NULL DEFAULT 8,
    overtime_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE payroll_holiday_policies
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    year       INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE payroll_holidays
(
    id         BIGSERIAL PRIMARY KEY,
    policy_id  BIGINT       NOT NULL REFERENCES payroll_holiday_policies (id) ON DELETE CASCADE,
    date       DATE         NOT NULL,
    name       VARCHAR(200) NOT NULL,
    type       VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC',
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_holidays_policy ON payroll_holidays (policy_id);

CREATE TABLE payroll_leave_policies
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    cycle      VARCHAR(10)  NOT NULL DEFAULT 'YEARLY',
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE payroll_leave_types
(
    id           BIGSERIAL PRIMARY KEY,
    policy_id    BIGINT       NOT NULL REFERENCES payroll_leave_policies (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    annual_count INT          NOT NULL DEFAULT 0,
    accrual      VARCHAR(20)  NOT NULL DEFAULT 'ALL_AT_ONCE',
    paid         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_leave_types_policy ON payroll_leave_types (policy_id);

CREATE TABLE payroll_profiles
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT UNIQUE NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    category          VARCHAR(20)   NOT NULL DEFAULT 'REGULAR',
    designation       VARCHAR(120),
    joining_date      DATE,
    monthly_ctc       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    basic             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    hra               NUMERIC(12, 2) NOT NULL DEFAULT 0,
    other_allowances  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    work_type         VARCHAR(10),
    work_rate         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    pf                BOOLEAN       NOT NULL DEFAULT FALSE,
    esic              BOOLEAN       NOT NULL DEFAULT FALSE,
    pt                BOOLEAN       NOT NULL DEFAULT FALSE,
    bank_account      VARCHAR(40),
    ifsc              VARCHAR(20),
    bank_name         VARCHAR(120),
    pan               VARCHAR(15),
    shift_id          BIGINT REFERENCES payroll_shifts (id) ON DELETE SET NULL,
    holiday_policy_id BIGINT REFERENCES payroll_holiday_policies (id) ON DELETE SET NULL,
    leave_policy_id   BIGINT REFERENCES payroll_leave_policies (id) ON DELETE SET NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_profiles_user ON payroll_profiles (user_id);

-- Seed sensible defaults so Setup isn't empty on first load — mirrors the frontend's seed data.
INSERT INTO payroll_shifts (name, start_time, end_time, weekly_offs, grace_minutes, half_day_hours, full_day_hours, overtime_enabled)
VALUES ('General (9 AM – 6 PM)', '09:00', '18:00', '0', 30, 4, 8, TRUE);

INSERT INTO payroll_holiday_policies (name, year)
VALUES ('India Public Holidays 2026', 2026);
INSERT INTO payroll_holidays (policy_id, date, name, type)
SELECT id, v.d, v.n, 'PUBLIC'
FROM payroll_holiday_policies,
     (VALUES ('2026-01-26'::date, 'Republic Day'),
             ('2026-03-04'::date, 'Holi'),
             ('2026-08-15'::date, 'Independence Day'),
             ('2026-10-02'::date, 'Gandhi Jayanti'),
             ('2026-11-08'::date, 'Diwali')) AS v (d, n)
WHERE payroll_holiday_policies.name = 'India Public Holidays 2026';

INSERT INTO payroll_leave_policies (name, cycle)
VALUES ('Standard Leave Policy', 'YEARLY');
INSERT INTO payroll_leave_types (policy_id, name, annual_count, accrual, paid)
SELECT id, v.n, v.c, v.a, TRUE
FROM payroll_leave_policies,
     (VALUES ('Casual Leave', 12, 'MONTHLY'),
             ('Sick Leave', 6, 'ALL_AT_ONCE'),
             ('Earned Leave', 12, 'MONTHLY')) AS v (n, c, a)
WHERE payroll_leave_policies.name = 'Standard Leave Policy';
