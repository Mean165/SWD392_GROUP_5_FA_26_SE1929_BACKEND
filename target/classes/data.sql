-- =========================================================
-- 1. SEED ROLES
-- =========================================================
INSERT INTO role (role_id, role_code, role_name, description, created_at)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'AD', 'Admin', 'Administrator role', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'LE', 'Lecturer', 'Lecturer role', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'ST', 'Student', 'Student role', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- =========================================================
-- 2. SEED ADMIN USER
-- =========================================================
INSERT INTO app_user (user_id, role_id, full_name, email, password_hash, student_or_staff_code, is_active, created_at)
VALUES 
    ('00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000001', 'System Administrator', 'admin@aiviva.com', '$2a$10$74Dw4kFjm/k23n2bATEfTePOkKhsPa9ASYmby2giYYzf897ygRASK', 'AD1', true, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
