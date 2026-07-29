-------------------------------------------------
-- Self-service Payroll access.
--
-- Every employee should be able to see THEIR OWN payroll (attendance, payslips, loans,
-- reimbursements) with the single login they already use for the rest of the ERP — a project
-- manager shouldn't need a second credential just to view their salary slip.
--
-- So grant PAYROLL:VIEW (view only) to the non-admin roles. The frontend treats VIEW-without-a
-- manage-action as "self-service" and shows only the signed-in user's own records; the manage
-- actions (CREATE/EDIT/DELETE/APPROVE) stay with Super Admin, who alone sees the full module
-- (all staff, salaries, payroll runs and approvals).
--
-- NOTE: this file was restored to keep the migration source in sync with the applied database
-- history (version 21 is already recorded in flyway_schema_history). It is idempotent.
-------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.module_id = (SELECT id FROM modules WHERE code = 'PAYROLL')
WHERE r.name IN ('Project Manager', 'Team Member', 'Site Supervisor')
  AND p.action = 'VIEW'
ON CONFLICT DO NOTHING;
