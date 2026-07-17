-------------------------------------------------
-- Taskopad becomes its own permission module (was riding on PROJECT:* authorities).
-- Adds a TASKOPAD row to the role permission matrix with the usual 5 actions.
-------------------------------------------------
INSERT INTO modules (code, name, created_at, updated_at)
VALUES ('TASKOPAD', 'Taskopad', now(), now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (module_id, action, created_at, updated_at)
SELECT m.id, a.action, now(), now()
FROM modules m
         CROSS JOIN (VALUES ('VIEW'), ('CREATE'), ('EDIT'), ('DELETE'), ('APPROVE')) AS a (action)
WHERE m.code = 'TASKOPAD'
ON CONFLICT (module_id, action) DO NOTHING;

-- Super Admin keeps full access (it is wired to every permission).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.module_id = (SELECT id FROM modules WHERE code = 'TASKOPAD')
WHERE r.name = 'Super Admin'
ON CONFLICT DO NOTHING;

-- Team Member gets Taskopad view/create/edit (parallels their existing PROJECT perms) so they keep
-- Taskopad access after the endpoints move from PROJECT:* to TASKOPAD:*.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.module_id = (SELECT id FROM modules WHERE code = 'TASKOPAD')
WHERE r.name = 'Team Member'
  AND p.action IN ('VIEW', 'CREATE', 'EDIT')
ON CONFLICT DO NOTHING;
