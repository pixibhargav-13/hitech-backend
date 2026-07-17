-------------------------------------------------
-- Seed the ERP modules (matches the frontend nav: src/lib/nav.ts) + User Management
-------------------------------------------------
INSERT INTO modules (code, name, created_at, updated_at)
VALUES ('DASHBOARD', 'Dashboard', now(), now()),
       ('REPORT', 'Report', now(), now()),
       ('PROJECT', 'Project', now(), now()),
       ('TEAM_SCHEDULE', 'Team Schedule', now(), now()),
       ('FINANCE', 'Finance', now(), now()),
       ('PAYROLL', 'Payroll', now(), now()),
       ('CRM', 'CRM', now(), now()),
       ('PROCUREMENT', 'Procurement', now(), now()),
       ('WAREHOUSE', 'Warehouse', now(), now()),
       ('EQUIPMENT', 'Equipment', now(), now()),
       ('ASSET', 'Asset', now(), now()),
       ('LIBRARY', 'Library', now(), now()),
       ('SETTINGS', 'Setting', now(), now()),
       ('SERVICES', 'Services', now(), now()),
       ('USER_MANAGEMENT', 'User Management', now(), now())
ON CONFLICT (code) DO NOTHING;

-------------------------------------------------
-- Every module gets the same 5 actions: VIEW, CREATE, EDIT, DELETE, APPROVE
-------------------------------------------------
INSERT INTO permissions (module_id, action, created_at, updated_at)
SELECT m.id, a.action, now(), now()
FROM modules m
         CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('APPROVE')) AS a (action)
ON CONFLICT (module_id, action) DO NOTHING;
