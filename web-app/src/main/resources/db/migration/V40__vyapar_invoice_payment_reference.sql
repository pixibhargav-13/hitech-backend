-------------------------------------------------
-- Cheque / NEFT number for the amount received on a document.
--
-- Vyapar shows a "Reference No." next to Payment Type on the invoice form whenever money is
-- received with the document. We rendered the field with nowhere to store it; this gives it a home
-- so the control isn't decorative.
-------------------------------------------------
ALTER TABLE vyapar_invoices
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(120);
