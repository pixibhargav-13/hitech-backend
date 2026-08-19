-- Let a supplier fill in their own quote from a link.
--
-- This is the half of procurement that decides whether the module replaces the tool the client
-- pays for or merely sits beside it. Until now every price had to be re-typed by the buyer: six
-- suppliers on one enquiry meant keying six quotes by hand.
--
-- Email is not wired yet, so the link is copied and sent over WhatsApp. The token is what makes
-- that safe to do.

-- One token per supplier per enquiry, not one per enquiry: the link identifies WHO is quoting, so
-- a supplier cannot see or overwrite a rival's prices, and a leaked link exposes exactly one
-- supplier's own quote rather than the whole comparison.
ALTER TABLE procurement_rfq_suppliers ADD COLUMN share_token VARCHAR(64);

-- Long random tokens, generated in the service. Unique so a lookup by token is unambiguous.
CREATE UNIQUE INDEX uq_proc_supplier_token ON procurement_rfq_suppliers (share_token)
    WHERE share_token IS NOT NULL;

-- When the supplier last opened the link, so "sent but never looked at" is distinguishable from
-- "looked at and chose not to quote" — the first needs a resend, the second a phone call.
ALTER TABLE procurement_rfq_suppliers ADD COLUMN opened_at TIMESTAMP(6);

-- Where the quote came from. A price the buyer typed in and one the supplier submitted carry
-- different weight in a dispute, and only the second can be pointed at as "your own figure".
ALTER TABLE procurement_quotes ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'BUYER';
COMMENT ON COLUMN procurement_quotes.source IS 'BUYER = keyed in by us, VENDOR = submitted by the supplier';

-- Submitted quotes lock so a supplier cannot revise silently after the comparison has been read.
-- The buyer can unlock to invite a revision, which is what their current tool calls "Unlock".
ALTER TABLE procurement_quotes ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE procurement_quotes ADD COLUMN submitted_at TIMESTAMP(6);
