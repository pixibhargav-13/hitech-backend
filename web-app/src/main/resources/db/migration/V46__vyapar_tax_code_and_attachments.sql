-- Vyapar parity: the Tax column is a named GST code, not just a rate, and a document can carry a
-- description, a photo and a supporting file.
--
-- Why a code and not only a percent: Vyapar's Tax picker offers IGST@18% and GST@18% as separate
-- entries. Both charge 18%, but GST@ splits into CGST+SGST for an intra-state supply while IGST@
-- is a single inter-state levy, and GSTR-1 reports them in different columns. Storing only the
-- number loses that distinction. NONE and EXEMPTED are both 0% but are also reported differently
-- (exempt supplies must be declared; untaxed lines are not), which the number can't tell apart.

ALTER TABLE vyapar_invoice_lines ADD COLUMN tax_code VARCHAR(20);

-- Purchase lines carry the input-tax-credit claim Vyapar asks for under the rate.
ALTER TABLE vyapar_invoice_lines ADD COLUMN itc_eligibility VARCHAR(40);

-- Backfill: every existing line was entered through the old GST-only picker, so an intra-state
-- GST@<rate> is the faithful reading of what was meant. 0% becomes NONE rather than EXEMPTED —
-- nothing in the old UI could express "exempt", so claiming it now would be inventing data.
UPDATE vyapar_invoice_lines
   SET tax_code = CASE
                    WHEN tax_percent IS NULL OR tax_percent = 0 THEN 'NONE'
                    -- Trim a trailing ".00" so 18.00 reads as GST@18%, but 0.25 keeps its decimals.
                    ELSE 'GST@' || TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM tax_percent::text)) || '%'
                  END
 WHERE tax_code IS NULL;

-- Purchase lines default to the commonest claim; sale lines have no ITC concept at all.
UPDATE vyapar_invoice_lines l
   SET itc_eligibility = 'Eligible for ITC - Input'
  FROM vyapar_invoices i
 WHERE l.invoice_id = i.id
   AND i.doc_type IN ('PURCHASE', 'PURCHASE_RETURN')
   AND l.itc_eligibility IS NULL;

-- Document-level attachments (Vyapar's ADD DESCRIPTION / ADD IMAGE / ADD DOCUMENT).
-- Images and files are held inline as data URLs, the same approach the firm logo and item photos
-- already use, so no file-storage service is needed to ship this.
ALTER TABLE vyapar_invoices ADD COLUMN description TEXT;
ALTER TABLE vyapar_invoices ADD COLUMN image_data_url TEXT;
ALTER TABLE vyapar_invoices ADD COLUMN document_name VARCHAR(255);
ALTER TABLE vyapar_invoices ADD COLUMN document_data_url TEXT;
