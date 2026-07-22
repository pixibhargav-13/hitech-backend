-------------------------------------------------
-- Bring the Vyapar documents up to the real app's invoice form: per-line description/unit and
-- line-level discount, plus header-level place of supply, terms, round-off and % discount.
-------------------------------------------------
ALTER TABLE vyapar_invoice_lines
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE vyapar_invoice_lines
    ADD COLUMN IF NOT EXISTS unit VARCHAR(30);
-- Discount can be entered either as a percentage or a flat amount; both are stored.
ALTER TABLE vyapar_invoice_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE vyapar_invoice_lines
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(16, 2) NOT NULL DEFAULT 0;
-- Tax amount for the line, kept alongside the percent so the printed invoice can show both.
ALTER TABLE vyapar_invoice_lines
    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(16, 2) NOT NULL DEFAULT 0;

-- "Cash" settles immediately, "Credit" leaves a balance — Vyapar's top-of-form toggle.
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS is_cash BOOLEAN NOT NULL DEFAULT TRUE;
-- GST place of supply (state), needed for correct IGST vs CGST/SGST treatment.
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS state_of_supply VARCHAR(120);
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS invoice_prefix VARCHAR(40);
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS terms VARCHAR(1000);
-- Discount entered as a percentage of sub-total (the flat amount lives in `discount`).
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS round_off NUMERIC(16, 2) NOT NULL DEFAULT 0;
