-- Seed initial roles if not exists
INSERT INTO roles (id, code, description)
VALUES 
    (1, 'AD', 'Administrator'),
    (2, 'LE', 'Lecturer'),
    (3, 'ST', 'Student')
ON CONFLICT (id) DO NOTHING;

-- Seed initial admin user if not exists
INSERT INTO users (id, full_name, email, phone_number, student_or_staff_code, department, is_active, role_id, created_at, updated_at)
VALUES 
    (1, 'System Administrator', 'admin@aiviva.com', '0123456789', 'AD1', 'IT', true, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Seed initial admin user authentication if not exists
INSERT INTO user_authentication (id, username, password_hash, enabled, provider, user_id)
VALUES 
    (1, 'admin@aiviva.com', '$2a$10$74Dw4kFjm/k23n2bATEfTePOkKhsPa9ASYmby2giYYzf897ygRASK', true, 'LOCAL', 1)
ON CONFLICT (id) DO NOTHING;
