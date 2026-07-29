-------------------------------------------------
-- Payroll attendance — one row per member per calendar date, real backend store for the punch
-- flow (GPS + face verified from the client) and the admin muster roll.
--
-- Keyed by (user_id, date) so a member has at most one attendance record per day; punch-in and
-- punch-out both update that same row. Face-verified selfies are NOT persisted here yet — we keep
-- only the numeric face_score (proof of match) and GPS coords, to keep rows small. Selfies move
-- to object storage in a later phase.
-------------------------------------------------

CREATE TABLE payroll_attendance
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    date              DATE         NOT NULL,
    code              VARCHAR(4)   NOT NULL DEFAULT 'NM',
    in_time           VARCHAR(5),
    out_time          VARCHAR(5),
    overtime_hours    NUMERIC(5, 2) NOT NULL DEFAULT 0,
    fine_hours        NUMERIC(5, 2) NOT NULL DEFAULT 0,
    project_id        BIGINT REFERENCES projects (id) ON DELETE SET NULL,
    punch_in_lat      NUMERIC(10, 6),
    punch_in_lng      NUMERIC(10, 6),
    punch_out_lat     NUMERIC(10, 6),
    punch_out_lng     NUMERIC(10, 6),
    face_score_in     NUMERIC(4, 3),
    face_score_out    NUMERIC(4, 3),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_attendance_user_date UNIQUE (user_id, date)
);
CREATE INDEX idx_payroll_attendance_date ON payroll_attendance (date);
CREATE INDEX idx_payroll_attendance_user_date ON payroll_attendance (user_id, date);
CREATE INDEX idx_payroll_attendance_project_date ON payroll_attendance (project_id, date);
