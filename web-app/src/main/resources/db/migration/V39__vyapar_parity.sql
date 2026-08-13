-------------------------------------------------
-- Vyapar parity, slice 1.
--
-- Brings four behaviours the real Vyapar desktop app has and our clone didn't:
--   1. Cancelling an invoice without deleting it (it stays in the books, struck out, and stops
--      counting towards balances).
--   2. Linking one payment across many invoices — Vyapar's "Link Payment to Txns". Until now a
--      payment could reference a single invoice, so a lump-sum receipt could not be split, and
--      the "Unused" status in the party ledger had nothing to compute from.
--   3. A module-wide settings row, so decimal places / round-off mode / prefixes stop being
--      hardcoded in the frontend.
--   4. Per-document history, which backs the "View History" row action.
-------------------------------------------------

-- 1. Cancelled invoices -------------------------------------------------
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS cancelled BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Payment ↔ invoice links --------------------------------------------
-- A payment with no links is entirely "unused"; the sum of its links is how much has been
-- applied. Deleting the payment removes its links.
CREATE TABLE IF NOT EXISTS vyapar_payment_links (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  BIGINT         NOT NULL REFERENCES vyapar_payments (id) ON DELETE CASCADE,
    invoice_id  BIGINT         NOT NULL REFERENCES vyapar_invoices (id) ON DELETE CASCADE,
    amount      NUMERIC(16, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT uq_vyapar_payment_link UNIQUE (payment_id, invoice_id)
);

CREATE INDEX IF NOT EXISTS idx_vyapar_payment_links_payment ON vyapar_payment_links (payment_id);
CREATE INDEX IF NOT EXISTS idx_vyapar_payment_links_invoice ON vyapar_payment_links (invoice_id);

-- Existing payments already point at a single invoice; carry those over so history stays intact.
INSERT INTO vyapar_payment_links (payment_id, invoice_id, amount, created_at, updated_at)
SELECT p.id, p.invoice_id, p.amount, NOW(), NOW()
FROM vyapar_payments p
WHERE p.invoice_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM vyapar_invoices i WHERE i.id = p.invoice_id)
ON CONFLICT (payment_id, invoice_id) DO NOTHING;

-- 3. Module settings ------------------------------------------------------
-- Single row (id = 1). Mirrors the tabs of Vyapar's Settings screen; only the switches that
-- actually change behaviour today are columns, the rest arrive as we implement them.
CREATE TABLE IF NOT EXISTS vyapar_settings (
    id                      BIGSERIAL PRIMARY KEY,
    amount_decimals         INT         NOT NULL DEFAULT 3,
    quantity_decimals       INT         NOT NULL DEFAULT 3,
    round_off_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    round_off_mode          VARCHAR(20) NOT NULL DEFAULT 'NEAREST',
    round_off_to            INT         NOT NULL DEFAULT 1,
    due_dates_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    link_payments_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    item_wise_tax           BOOLEAN     NOT NULL DEFAULT TRUE,
    item_wise_discount      BOOLEAN     NOT NULL DEFAULT TRUE,
    display_purchase_price  BOOLEAN     NOT NULL DEFAULT TRUE,
    transaction_wise_tax    BOOLEAN     NOT NULL DEFAULT FALSE,
    transaction_wise_disc   BOOLEAN     NOT NULL DEFAULT TRUE,
    estimate_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    proforma_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    orders_enabled          BOOLEAN     NOT NULL DEFAULT TRUE,
    delivery_challan_enabled BOOLEAN    NOT NULL DEFAULT TRUE,
    -- Per-document-type invoice prefixes, e.g. {"SALE":"GJ/RA/26-27/","PURCHASE":""}.
    prefixes                TEXT,
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP
);

INSERT INTO vyapar_settings (id, created_at, updated_at)
SELECT 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM vyapar_settings WHERE id = 1);

-- The insert above supplies id explicitly, which leaves BIGSERIAL's sequence at 0; nudge it past
-- the seeded row so a later insert can't collide on the primary key.
SELECT setval(pg_get_serial_sequence('vyapar_settings', 'id'),
              GREATEST((SELECT COALESCE(MAX(id), 1) FROM vyapar_settings), 1));

-- 4. Document history -----------------------------------------------------
CREATE TABLE IF NOT EXISTS vyapar_invoice_history (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT       NOT NULL REFERENCES vyapar_invoices (id) ON DELETE CASCADE,
    action      VARCHAR(30)  NOT NULL,
    detail      VARCHAR(500),
    user_id     BIGINT,
    at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vyapar_invoice_history_invoice
    ON vyapar_invoice_history (invoice_id, id DESC);
