-------------------------------------------------
-- Payroll becomes its own permission module (staff, attendance, salary, loans, reimbursements).
-- Mirrors the Taskopad/Audit module-seeding pattern: a PAYROLL row plus the usual 5 actions,
-- wired to Super Admin. Actual payroll tables land in a later migration once the UI is approved.
-------------------------------------------------
INSERT INTO modules (code, name, created_at, updated_at)
VALUES ('PAYROLL', 'Payroll', now(), now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (module_id, action, created_at, updated_at)
SELECT m.id, a.action, now(), now()
FROM modules m
         CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('APPROVE')) AS a (action)
WHERE m.code = 'PAYROLL'
ON CONFLICT (module_id, action) DO NOTHING;

-- Super Admin keeps full access (it is wired to every permission).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.module_id = (SELECT id FROM modules WHERE code = 'PAYROLL')
WHERE r.name = 'Super Admin'
ON CONFLICT DO NOTHING;
