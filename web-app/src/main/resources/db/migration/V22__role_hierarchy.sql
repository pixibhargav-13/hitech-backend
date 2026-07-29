-------------------------------------------------
-- Role reporting hierarchy (the org "ladder").
--
-- Each role may report to ONE parent role, e.g. Site Engineer -> Project Manager -> Director.
-- The frontend renders this as a tree, and a person's real manager is derived from their role's
-- parent (resolved to the right person via their project/office). Null parent = top of the ladder.
-------------------------------------------------
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS reports_to_role_id BIGINT;

ALTER TABLE roles
    ADD CONSTRAINT fk_roles_reports_to
        FOREIGN KEY (reports_to_role_id) REFERENCES roles (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_roles_reports_to ON roles (reports_to_role_id);
