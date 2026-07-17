-------------------------------------------------
-- Audit becomes its own permission module. Only roles granted AUDIT:VIEW can read the trail.
-------------------------------------------------
INSERT INTO modules (code, name, created_at, updated_at)
VALUES ('AUDIT', 'Audit', now(), now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (module_id, action, created_at, updated_at)
SELECT m.id, a.action, now(), now()
FROM modules m
         CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('APPROVE')) AS a (action)
WHERE m.code = 'AUDIT'
ON CONFLICT (module_id, action) DO NOTHING;

-- Super Admin keeps full access (it is wired to every permission).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.module_id = (SELECT id FROM modules WHERE code = 'AUDIT')
WHERE r.name = 'Super Admin'
ON CONFLICT DO NOTHING;
