-------------------------------------------------
-- Walk-in billing details on a cash document.
--
-- In Vyapar a cash bill doesn't need a saved party: the Customer field becomes
-- "Billing Name (Optional)" and a Billing Address box appears beside it, so a counter sale can be
-- raised for someone who will never be a ledger account. Without these columns those fields had
-- nowhere to persist and the printed bill came out addressed to nobody.
-------------------------------------------------
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS billing_name    VARCHAR(200),
    ADD COLUMN IF NOT EXISTS billing_address VARCHAR(500);
