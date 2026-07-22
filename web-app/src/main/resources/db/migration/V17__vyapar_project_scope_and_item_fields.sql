-------------------------------------------------
-- Scope every Vyapar book to a construction project, and let items persist the
-- category & description the frontend already sends (previously dropped on save).
-- Columns are nullable so existing rows stay valid under ddl-auto: validate.
-------------------------------------------------

ALTER TABLE vyapar_parties  ADD COLUMN IF NOT EXISTS project_id BIGINT;
ALTER TABLE vyapar_items    ADD COLUMN IF NOT EXISTS project_id BIGINT;
ALTER TABLE vyapar_invoices ADD COLUMN IF NOT EXISTS project_id BIGINT;
ALTER TABLE vyapar_payments ADD COLUMN IF NOT EXISTS project_id BIGINT;

ALTER TABLE vyapar_items ADD COLUMN IF NOT EXISTS category    VARCHAR(120);
ALTER TABLE vyapar_items ADD COLUMN IF NOT EXISTS description VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_vyapar_parties_project  ON vyapar_parties (project_id);
CREATE INDEX IF NOT EXISTS idx_vyapar_items_project    ON vyapar_items (project_id);
CREATE INDEX IF NOT EXISTS idx_vyapar_invoices_project ON vyapar_invoices (project_id);
CREATE INDEX IF NOT EXISTS idx_vyapar_payments_project ON vyapar_payments (project_id);
CREATE INDEX IF NOT EXISTS idx_vyapar_items_category   ON vyapar_items (category);
