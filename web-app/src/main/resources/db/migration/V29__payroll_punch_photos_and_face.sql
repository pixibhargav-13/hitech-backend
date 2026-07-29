-------------------------------------------------
-- V29 — persist punch selfies + face enrolment on the server, so the punch flow works across
-- devices (not only the browser that enrolled) and the captured selfie shows in the ERP muster /
-- attendance views.
--   * payroll_attendance gains punch_in_photo / punch_out_photo (small base64 JPEG data URLs).
--   * payroll_face_enrollment holds one reference faceprint + selfie per member (self-service).
-------------------------------------------------

ALTER TABLE payroll_attendance
    ADD COLUMN punch_in_photo  TEXT,
    ADD COLUMN punch_out_photo TEXT;

CREATE TABLE payroll_face_enrollment
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    descriptor TEXT      NOT NULL,   -- the 128-float faceprint, comma-joined
    photo      TEXT,                 -- small base64 JPEG selfie, for the admin view
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_payroll_face_enrollment_user UNIQUE (user_id)
);
