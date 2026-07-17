-------------------------------------------------
-- Super Admin: system role, wired to every permission. Cannot be deleted (is_system = true).
-- The bootstrap admin user (BootstrapAdminRunner) is assigned this role on first startup.
-------------------------------------------------
INSERT INTO roles (name, description, is_system, created_at, updated_at)
VALUES ('Super Admin', 'Full access to every module and feature', TRUE, now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'Super Admin'
ON CONFLICT DO NOTHING;
