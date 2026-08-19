-- Sample procurement data, so the client can try the module against something that looks like
-- their own work rather than an empty screen.
--
-- This file is NOT part of the schema. It lives in db/seed, a separate Flyway location that is off
-- unless an environment asks for it:
--
--     FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed
--
-- Version 900 leaves the whole V1..V899 band free for real schema, so a seed can never sit between
-- two migrations and change the order they apply in.
--
-- Three rules this file follows, because it runs against a live database:
--
--   * It invents no parties and no projects. Vendors are whatever suppliers already exist and the
--     project is the client's own; if the database has neither, the whole thing skips quietly
--     rather than manufacturing a fake org to hang rows off.
--   * It writes nothing outside procurement_*. No invoices, no ledger entries, no stock movements —
--     nothing that would move a balance the client reads as real money.
--   * It is skipped entirely if procurement already has data, so switching the seed location on
--     against a database somebody has already been using does not double it up.
--
-- To remove it later:  DELETE FROM procurement_rfqs WHERE notes = 'Sample data';
--                      DELETE FROM procurement_work_orders WHERE notes = 'Sample data';

DO $$
DECLARE
    v_project   BIGINT;
    v_vendors   BIGINT[];
    v_rfq       BIGINT;
    v_wo        BIGINT;
    v_line1     BIGINT;
    v_line2     BIGINT;
    v_line3     BIGINT;
    v_quote     BIGINT;
    v_today     DATE := CURRENT_DATE;
BEGIN
    -- Already in use? Leave it alone. Sample rows on top of real enquiries is worse than no sample.
    IF EXISTS (SELECT 1 FROM procurement_rfqs) OR EXISTS (SELECT 1 FROM procurement_work_orders) THEN
        RAISE NOTICE 'Procurement already has data — sample skipped.';
        RETURN;
    END IF;

    -- The client's own project, by preference the Rajkot books; otherwise whichever exists.
    SELECT id INTO v_project FROM projects
     ORDER BY (name = 'HITECHRAJKOT') DESC, id
     LIMIT 1;

    -- Real suppliers, so the names on screen are people they actually buy from.
    SELECT array_agg(id ORDER BY id) INTO v_vendors
      FROM (SELECT id FROM vyapar_parties
             WHERE is_active
             ORDER BY (party_type = 'SUPPLIER') DESC, id
             LIMIT 4) s;

    IF v_vendors IS NULL OR array_length(v_vendors, 1) < 2 THEN
        RAISE NOTICE 'Fewer than two parties on this database — sample skipped.';
        RETURN;
    END IF;

    -- ============================ Enquiry 1: three suppliers, one skips a line ============

    INSERT INTO procurement_rfqs (rfq_no, title, project_id, status, rfq_date, due_by, tax_type,
                                  bidding_start_date, bidding_end_date, delivery_date, terms,
                                  bill_to_name, ship_same_as_bill, notes, created_at, updated_at)
    VALUES ('RFQ-SAMPLE-001', 'Valves — Pedak Road chambers', v_project, 'Responses In',
            (v_today - 6)::TEXT, (v_today + 8)::TEXT, 'ITEM',
            (v_today - 6)::TEXT, (v_today + 8)::TEXT, (v_today + 20)::TEXT,
            'Rates inclusive of freight to site. Payment 30 days from receipt of material.',
            'Hi-Tech Construction', TRUE, 'Sample data', NOW(), NOW())
    RETURNING id INTO v_rfq;

    INSERT INTO procurement_rfq_lines (rfq_id, item_name, specification, hsn_code, unit, quantity,
                                       budget_rate, sort_order, created_at, updated_at)
    VALUES (v_rfq, '450MM Sluice Valve', 'Kirloskar / IVI / GM', '8481', 'Nos', 1, 140000, 0, NOW(), NOW())
    RETURNING id INTO v_line1;

    INSERT INTO procurement_rfq_lines (rfq_id, item_name, specification, hsn_code, unit, quantity,
                                       budget_rate, sort_order, created_at, updated_at)
    VALUES (v_rfq, '150MM Air Valve', 'Double acting', '8481', 'Nos', 3, 18000, 1, NOW(), NOW())
    RETURNING id INTO v_line2;

    -- Everyone invited gets a quote link. The tokens are readable on purpose: these are sample
    -- rows on a demo enquiry, and a link somebody can retype is easier to show a client than 43
    -- random characters. Real enquiries mint 256 bits of randomness in RfqService.
    INSERT INTO procurement_rfq_suppliers (rfq_id, vendor_party_id, sent_at, share_token, created_at, updated_at)
    SELECT v_rfq, v, NOW(), 'sample-rfq1-' || v, NOW(), NOW()
      FROM unnest(v_vendors[1:3]) AS v;

    -- Quote A — dearest, quotes both lines.
    INSERT INTO procurement_quotes (rfq_id, vendor_party_id, version, received_on, delivery_days,
                                    discount, charges, tax_percent, note, source, locked, created_at, updated_at)
    VALUES (v_rfq, v_vendors[1], 1, (v_today - 2)::TEXT, 12, 0, 0, 18, 'Ex-works Coimbatore', 'BUYER', FALSE, NOW(), NOW())
    RETURNING id INTO v_quote;
    INSERT INTO procurement_quote_lines (quote_id, rfq_line_id, rate, created_at, updated_at)
    VALUES (v_quote, v_line1, 155051, NOW(), NOW()), (v_quote, v_line2, 21200, NOW(), NOW());

    -- Quote B — cheapest overall, submitted by the supplier through their own link.
    INSERT INTO procurement_quotes (rfq_id, vendor_party_id, version, received_on, delivery_days,
                                    discount, charges, tax_percent, note, source, locked, submitted_at, created_at, updated_at)
    VALUES (v_rfq, v_vendors[2], 1, (v_today - 1)::TEXT, 7, 0, 0, 18, NULL, 'VENDOR', TRUE, NOW(), NOW(), NOW())
    RETURNING id INTO v_quote;
    INSERT INTO procurement_quote_lines (quote_id, rfq_line_id, rate, created_at, updated_at)
    VALUES (v_quote, v_line1, 106290, NOW(), NOW()), (v_quote, v_line2, 19460, NOW(), NOW());

    -- Quote C — skips the air valve entirely. This is the "No quote" cell, and it is the one case
    -- a comparative statement has to get right: blank means not offered, never free.
    INSERT INTO procurement_quotes (rfq_id, vendor_party_id, version, received_on, delivery_days,
                                    discount, charges, tax_percent, note, source, locked, created_at, updated_at)
    VALUES (v_rfq, v_vendors[3], 1, (v_today - 1)::TEXT, 5, 2000, 0, 18, 'Kirloskar make', 'BUYER', FALSE, NOW(), NOW())
    RETURNING id INTO v_quote;
    INSERT INTO procurement_quote_lines (quote_id, rfq_line_id, rate, created_at, updated_at)
    VALUES (v_quote, v_line1, 108400, NOW(), NOW());

    -- ============================ Enquiry 2: two specialists, split award ==================

    INSERT INTO procurement_rfqs (rfq_no, title, project_id, status, rfq_date, due_by, tax_type,
                                  bidding_start_date, bidding_end_date, delivery_date, terms,
                                  bill_to_name, ship_same_as_bill, notes, created_at, updated_at)
    VALUES ('RFQ-SAMPLE-002', 'Cement & steel — slab casting', v_project, 'Responses In',
            (v_today - 4)::TEXT, (v_today + 5)::TEXT, 'ITEM',
            (v_today - 4)::TEXT, (v_today + 5)::TEXT, (v_today + 12)::TEXT,
            'Material to be unloaded at site. Test certificates to accompany each lot.',
            'Hi-Tech Construction', TRUE, 'Sample data', NOW(), NOW())
    RETURNING id INTO v_rfq;

    INSERT INTO procurement_rfq_lines (rfq_id, item_name, specification, hsn_code, unit, quantity,
                                       budget_rate, sort_order, created_at, updated_at)
    VALUES (v_rfq, 'Cement OPC 53', 'Ambuja / UltraTech', '2523', 'Bag', 100, 375, 0, NOW(), NOW())
    RETURNING id INTO v_line1;

    INSERT INTO procurement_rfq_lines (rfq_id, item_name, specification, hsn_code, unit, quantity,
                                       budget_rate, sort_order, created_at, updated_at)
    VALUES (v_rfq, 'TMT Bar 12mm', 'Fe 500D', '7214', 'Kg', 500, 61, 1, NOW(), NOW())
    RETURNING id INTO v_line2;

    INSERT INTO procurement_rfq_suppliers (rfq_id, vendor_party_id, sent_at, share_token, created_at, updated_at)
    SELECT v_rfq, v, NOW(), 'sample-rfq2-' || v, NOW(), NOW()
      FROM unnest(v_vendors[1:2]) AS v;

    -- One quotes only the cement, the other only the steel. Neither can win the whole enquiry —
    -- which is the case a one-winner-per-RFQ model cannot express, and why awards are per line.
    INSERT INTO procurement_quotes (rfq_id, vendor_party_id, version, received_on, delivery_days,
                                    discount, charges, tax_percent, source, locked, created_at, updated_at)
    VALUES (v_rfq, v_vendors[1], 1, (v_today - 1)::TEXT, 2, 0, 0, 18, 'BUYER', FALSE, NOW(), NOW())
    RETURNING id INTO v_quote;
    INSERT INTO procurement_quote_lines (quote_id, rfq_line_id, rate, created_at, updated_at)
    VALUES (v_quote, v_line1, 372, NOW(), NOW());

    INSERT INTO procurement_quotes (rfq_id, vendor_party_id, version, received_on, delivery_days,
                                    discount, charges, tax_percent, note, source, locked, created_at, updated_at)
    VALUES (v_rfq, v_vendors[2], 1, (v_today - 1)::TEXT, 9, 0, 0, 18,
            'Lowest on steel, but longest lead time', 'BUYER', FALSE, NOW(), NOW())
    RETURNING id INTO v_quote;
    INSERT INTO procurement_quote_lines (quote_id, rfq_line_id, rate, created_at, updated_at)
    VALUES (v_quote, v_line2, 60.80, NOW(), NOW());

    -- ============================ Enquiry 3: sent, nothing back yet =======================

    INSERT INTO procurement_rfqs (rfq_no, title, project_id, status, rfq_date, due_by, tax_type,
                                  bidding_start_date, bidding_end_date, bill_to_name,
                                  ship_same_as_bill, notes, created_at, updated_at)
    VALUES ('RFQ-SAMPLE-003', 'Diesel — site generator', v_project, 'Sent',
            (v_today - 1)::TEXT, (v_today + 4)::TEXT, 'ITEM',
            (v_today - 1)::TEXT, (v_today + 4)::TEXT, 'Hi-Tech Construction', TRUE,
            'Sample data', NOW(), NOW())
    RETURNING id INTO v_rfq;

    INSERT INTO procurement_rfq_lines (rfq_id, item_name, hsn_code, unit, quantity, sort_order, created_at, updated_at)
    VALUES (v_rfq, 'Diesel', '2710', 'Litre', 200, 0, NOW(), NOW());

    INSERT INTO procurement_rfq_suppliers (rfq_id, vendor_party_id, sent_at, share_token, created_at, updated_at)
    SELECT v_rfq, v, NOW(), 'sample-rfq3-' || v, NOW(), NOW()
      FROM unnest(v_vendors[1:2]) AS v;

    -- ============================ Work order 1: measured, part billed =====================

    INSERT INTO procurement_work_orders (wo_no, title, project_id, vendor_party_id, status, wo_date,
                                         start_date, end_date, tax_percent, discount, charges,
                                         bank_account_name, bank_account_number, bank_ifsc,
                                         terms, notes, created_at, updated_at)
    VALUES ('WO-SAMPLE-001', 'Sewer line laying — Zone 8', v_project, v_vendors[1], 'In Progress',
            (v_today - 30)::TEXT, (v_today - 28)::TEXT, (v_today + 20)::TEXT, 18, 0, 0,
            'Sample Contractor', 'XXXXXXXXXXXX', 'HDFC0001234',
            '10% retention on every running bill, released on completion certificate.',
            'Sample data', NOW(), NOW())
    RETURNING id INTO v_wo;

    -- Measured lines: the quantity is the product of the dimensions, exactly as the service would
    -- have computed it. 4 x 1000 = 4000 rmt; 4 x 250 x 0.9 x 2 = 1800 cum.
    INSERT INTO procurement_work_order_items (work_order_id, item_name, description, unit,
                                              dim_n, dim_l, dim_w, dim_h, quantity, rate,
                                              progress_percent, sort_order, created_at, updated_at)
    VALUES (v_wo, 'HSC laying', '600mm dia, NP3', 'Rmt', 4, 1000, NULL, NULL, 4000, 110, 90, 0, NOW(), NOW()),
           (v_wo, 'IC construction', '1.2m x 1.2m chamber', 'Nos', 100, NULL, NULL, NULL, 100, 700, 60, 1, NOW(), NOW()),
           (v_wo, 'Excavation', 'Up to 2m depth', 'Cum', 4, 250, 0.9, 2, 1800, 180, 100, 2, NOW(), NOW());

    INSERT INTO procurement_subcon_bills (work_order_id, bill_no, bill_date, amount, retention,
                                          material_recovery, note, created_at, updated_at)
    VALUES (v_wo, 'RA-01', (v_today - 20)::TEXT, 98080, 9808, 0, 'First running bill', NOW(), NOW()),
           (v_wo, 'RA-02', (v_today - 6)::TEXT, 145000, 14500, 32000, 'Second running bill', NOW(), NOW());

    -- Material out of our store against his order, and what came back.
    INSERT INTO procurement_subcon_materials (work_order_id, item_name, unit, movement, quantity,
                                              rate, moved_on, created_at, updated_at)
    VALUES (v_wo, 'Cement OPC 53', 'Bag', 'ISSUE',    200, 380, (v_today - 25)::TEXT, NOW(), NOW()),
           (v_wo, 'Cement OPC 53', 'Bag', 'CONSUMED', 160,   0, (v_today - 7)::TEXT,  NOW(), NOW()),
           (v_wo, 'TMT Bar 12mm',  'Kg',  'ISSUE',    800,  62, (v_today - 24)::TEXT, NOW(), NOW()),
           (v_wo, 'TMT Bar 12mm',  'Kg',  'RETURN',    45,   0, (v_today - 5)::TEXT,  NOW(), NOW());

    -- ============================ Work order 2: billed past the order =====================

    INSERT INTO procurement_work_orders (wo_no, title, project_id, vendor_party_id, status, wo_date,
                                         start_date, end_date, tax_percent, discount, charges,
                                         terms, notes, created_at, updated_at)
    VALUES ('WO-SAMPLE-002', 'Fabrication work — cover slabs', v_project, v_vendors[2], 'Completed',
            (v_today - 60)::TEXT, (v_today - 58)::TEXT, (v_today - 10)::TEXT, 18, 0, 0,
            'Rate inclusive of material and consumables.', 'Sample data', NOW(), NOW())
    RETURNING id INTO v_wo;

    INSERT INTO procurement_work_order_items (work_order_id, item_name, description, unit,
                                              quantity, rate, progress_percent, sort_order, created_at, updated_at)
    VALUES (v_wo, 'MS fabrication', 'Angle and plate work', 'Kg', 342, 24, 100, 0, NOW(), NOW());

    -- Deliberately over the order value. It happens on real sites, and the screen is built to show
    -- it rather than quietly clamp the bar at 100%.
    INSERT INTO procurement_subcon_bills (work_order_id, bill_no, bill_date, amount, retention,
                                          material_recovery, note, created_at, updated_at)
    VALUES (v_wo, 'F-01', (v_today - 30)::TEXT, 8208, 0, 0, NULL, NOW(), NOW()),
           (v_wo, 'F-02', (v_today - 9)::TEXT,  2400, 0, 0, 'Extra items beyond order', NOW(), NOW());

    -- ============================ Work order 3: draft =====================================

    INSERT INTO procurement_work_orders (wo_no, title, project_id, vendor_party_id, status, wo_date,
                                         tax_percent, discount, charges, notes, created_at, updated_at)
    VALUES ('WO-SAMPLE-003', 'Painter work — chambers', v_project, v_vendors[2], 'Draft',
            v_today::TEXT, 0, 0, 0, 'Sample data', NOW(), NOW())
    RETURNING id INTO v_wo;

    INSERT INTO procurement_work_order_items (work_order_id, item_name, unit, dim_n, dim_l, dim_w,
                                              quantity, rate, progress_percent, sort_order, created_at, updated_at)
    VALUES (v_wo, 'Enamel painting', 'Sqm', 24, 1.2, 1.2, 34.56, 104, 0, 0, NOW(), NOW());

    RAISE NOTICE 'Sample procurement data inserted against project %.', v_project;
END $$;
