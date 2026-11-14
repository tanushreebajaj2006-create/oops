-- ==============================================================================
-- Flyway V3: reference and demo data
--
-- Categories, IT services, SLA policies and one demo account per role.
-- Sample incidents are NOT inserted here: they are created through the domain
-- model at application start-up (Phase 5 demo seeder), so each one gets a
-- consistent priority, SLA, assignment and complete audit trail.
--
-- DEMO ACCOUNTS (development and demonstration only - change before any real use)
--   Every account below uses the password:  Demo@12345
--   stored as a BCrypt hash (cost 10). Log in with the e-mail address.
-- ==============================================================================

-- ---- Incident categories (the nine from the brief) ---------------------------
INSERT INTO categories (code, name, active) VALUES
    ('HARDWARE',       'Hardware',       TRUE),
    ('SOFTWARE',       'Software',       TRUE),
    ('NETWORK',        'Network',        TRUE),
    ('DATABASE',       'Database',       TRUE),
    ('CLOUD',          'Cloud',          TRUE),
    ('SECURITY',       'Security',       TRUE),
    ('AUTHENTICATION', 'Authentication', TRUE),
    ('APPLICATION',    'Application',    TRUE),
    ('STORAGE',        'Storage',        TRUE);

-- ---- IT services and their business criticality -------------------------------
INSERT INTO it_services (code, name, criticality, active) VALUES
    ('PAYMENT',       'Payment Gateway',       'CRITICAL', TRUE),
    ('AUTH',          'Single Sign-On',        'CRITICAL', TRUE),
    ('CORE_DB',       'Core Database Cluster', 'CRITICAL', TRUE),
    ('EMAIL',         'Corporate E-mail',      'HIGH',     TRUE),
    ('VPN',           'Remote Access VPN',     'HIGH',     TRUE),
    ('ERP',           'ERP System',            'HIGH',     TRUE),
    ('CLOUD_STORAGE', 'Cloud File Storage',    'MEDIUM',   TRUE),
    ('HR_PORTAL',     'HR Self-Service Portal','MEDIUM',   TRUE),
    ('WIFI',          'Office Wi-Fi',          'MEDIUM',   TRUE),
    ('WIKI',          'Internal Wiki',         'LOW',      TRUE),
    ('PRINTING',      'Printing Service',      'LOW',      TRUE);

-- ---- SLA policies ----------------------------------------------------------------
-- Default policy: the table from the brief (P1 5m/30m, P2 15m/2h, P3 1h/8h, P4 4h/24h)
INSERT INTO sla_policies (name, service_code, warning_threshold) VALUES ('Standard SLA', NULL, 0.75);
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P1',   300,  1800 FROM sla_policies WHERE service_code IS NULL;
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P2',   900,  7200 FROM sla_policies WHERE service_code IS NULL;
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P3',  3600, 28800 FROM sla_policies WHERE service_code IS NULL;
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P4', 14400, 86400 FROM sla_policies WHERE service_code IS NULL;

-- Payments have tighter P1/P2 targets; P3/P4 fall back to the standard policy.
INSERT INTO sla_policies (name, service_code, warning_threshold) VALUES ('Payments Gold', 'PAYMENT', 0.70);
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P1',  120,  900 FROM sla_policies WHERE service_code = 'PAYMENT';
INSERT INTO sla_targets (policy_id, priority, response_seconds, resolution_seconds)
    SELECT id, 'P2',  600, 3600 FROM sla_policies WHERE service_code = 'PAYMENT';

-- ---- Users -----------------------------------------------------------------------
-- Administrator / IT manager
INSERT INTO users (id, user_role, full_name, email, password_hash, phone, active) VALUES
    ('A001', 'ADMINISTRATOR', 'Priya Menon', 'admin@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000001', TRUE);

-- Team leads (receive escalations; can also be assigned work)
INSERT INTO users (id, user_role, full_name, email, password_hash, phone, active,
                   experience_years, max_workload, available) VALUES
    ('L301', 'TEAM_LEAD', 'Meera Lal',    'meera.lal@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000301', TRUE, 11, 3, TRUE),
    ('L302', 'TEAM_LEAD', 'Rohan Iyer',   'rohan.iyer@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000302', TRUE, 9, 3, TRUE);

-- Technicians
INSERT INTO users (id, user_role, full_name, email, password_hash, phone, active,
                   experience_years, max_workload, available) VALUES
    ('T201', 'TECHNICIAN', 'Neha Sharma',  'neha.sharma@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000201', TRUE, 6, 5, TRUE),
    ('T202', 'TECHNICIAN', 'Arjun Patel',  'arjun.patel@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000202', TRUE, 3, 5, TRUE),
    ('T203', 'TECHNICIAN', 'Kavya Reddy',  'kavya.reddy@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000203', TRUE, 8, 5, TRUE),
    ('T204', 'TECHNICIAN', 'Vikram Singh', 'vikram.singh@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL,            TRUE, 2, 4, TRUE),
    ('T205', 'TECHNICIAN', 'Ananya Das',   'ananya.das@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000205', TRUE, 5, 5, TRUE),
    ('T206', 'TECHNICIAN', 'Farhan Ali',   'farhan.ali@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', '+919800000206', TRUE, 1, 3, TRUE);

INSERT INTO user_skills (user_id, category_code, proficiency) VALUES
    ('L301', 'NETWORK', 5), ('L301', 'APPLICATION', 4), ('L301', 'SECURITY', 4),
    ('L302', 'DATABASE', 5), ('L302', 'CLOUD', 5), ('L302', 'STORAGE', 4),
    ('T201', 'APPLICATION', 5), ('T201', 'SOFTWARE', 4), ('T201', 'AUTHENTICATION', 3),
    ('T202', 'NETWORK', 4), ('T202', 'HARDWARE', 3),
    ('T203', 'DATABASE', 4), ('T203', 'STORAGE', 3), ('T203', 'CLOUD', 3),
    ('T204', 'HARDWARE', 4), ('T204', 'SOFTWARE', 2),
    ('T205', 'SECURITY', 5), ('T205', 'AUTHENTICATION', 5), ('T205', 'NETWORK', 2),
    ('T206', 'SOFTWARE', 3), ('T206', 'APPLICATION', 2);

-- Employees (reporters)
INSERT INTO users (id, user_role, full_name, email, password_hash, phone, active, department) VALUES
    ('E101', 'EMPLOYEE', 'Asha Rao',      'asha.rao@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Finance'),
    ('E102', 'EMPLOYEE', 'Ravi Kumar',    'ravi.kumar@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Sales'),
    ('E103', 'EMPLOYEE', 'Sneha Joshi',   'sneha.joshi@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Human Resources'),
    ('E104', 'EMPLOYEE', 'Karan Mehta',   'karan.mehta@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Operations'),
    ('E105', 'EMPLOYEE', 'Divya Nair',    'divya.nair@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Marketing'),
    ('E106', 'EMPLOYEE', 'Siddharth Rao', 'siddharth.rao@servicedesk.example',
     '$2a$10$gY8MaKTaqnh4F8KZs/6u0.nTChVCgzy7SgZnQGClyRIyHkrwvprTG', NULL, TRUE, 'Engineering');
