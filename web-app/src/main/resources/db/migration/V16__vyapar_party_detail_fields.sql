-------------------------------------------------
-- Bring vyapar_parties up to Vyapar's own party form: GST type/state, shipping address,
-- party grouping, opening-balance date, credit limit and four configurable extra fields.
-------------------------------------------------
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS gst_type VARCHAR(60);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS state VARCHAR(120);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS shipping_address VARCHAR(500);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS party_group VARCHAR(120);
-- Date the opening balance is stated as of.
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS opening_date VARCHAR(30);
-- NULL means no credit limit for this party.
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(16, 2);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS field1 VARCHAR(255);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS field2 VARCHAR(255);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS field3 VARCHAR(255);
ALTER TABLE vyapar_parties ADD COLUMN IF NOT EXISTS field4 VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_vyapar_parties_group ON vyapar_parties (party_group);
