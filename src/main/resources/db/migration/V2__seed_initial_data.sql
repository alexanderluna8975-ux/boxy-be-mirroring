-- =============================================================================
-- V2__seed_initial_data.sql: Enterprise Seed Data for Boxy with Exact Role Matrix
-- =============================================================================

-- 1. SEED COMPANY
INSERT INTO companies (id, name, tax_id, email, phone, address, currency_code, currency_symbol, timezone, allow_negative_stock, is_active)
VALUES ('c0000000-0000-0000-0000-000000000001', 'Boxy Enterprise Corp', 'TAX-99887766-1', 'contact@boxy.com', '+1-555-0199', '100 Enterprise Way, Suite 400', 'USD', '$', 'UTC', FALSE, TRUE);

-- 2. SEED BRANCHES
INSERT INTO branches (id, company_id, code, name, address, phone, email, is_main, is_active)
VALUES 
('b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'BR-CENTRAL', 'Central Flagship Store', 'Av. Principal 101, Downtown', '+1-555-0101', 'central@boxy.com', TRUE, TRUE),
('b0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'BR-NORTH', 'North Mall Branch', 'Mall Plaza Norte, Local 45', '+1-555-0102', 'north@boxy.com', FALSE, TRUE);

-- 3. SEED WAREHOUSES
INSERT INTO warehouses (id, branch_id, code, name, is_default, is_active)
VALUES 
('w0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'WH-CEN-MAIN', 'Central Main Warehouse', TRUE, TRUE),
('w0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'WH-NOR-MAIN', 'North Store Warehouse', TRUE, TRUE);

-- 4. SEED USERS (Password for all: password123)
-- BCrypt hash: $2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga
INSERT INTO users (id, company_id, username, email, password_hash, first_name, last_name, status)
VALUES 
('u0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'owner', 'owner@boxy.com', '$2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga', 'Alexander', 'Luna (Owner)', 'ACTIVE'),
('u0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'admin', 'admin@boxy.com', '$2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga', 'Carlos', 'Admin', 'ACTIVE'),
('u0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'salesman1', 'salesman1@boxy.com', '$2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga', 'Laura', 'Ventas Senior', 'ACTIVE'),
('u0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000001', 'salesman2', 'salesman2@boxy.com', '$2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga', 'Pedro', 'Ventas Junior', 'ACTIVE'),
('u0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000001', 'store', 'store@boxy.com', '$2a$10$IL4nYPS7EtG2UKVMri6biuF5G7Br3EPvCP9oJhAgaI7Uxo34t3Vga', 'Mateo', 'Tienda & Stock', 'ACTIVE');

-- 5. SEED PERMISSIONS (Granular Matrix)
INSERT INTO permissions (id, module, action, code, description)
VALUES 
('p0000000-0000-0000-0000-000000000001', 'administration', 'users:manage', 'administration:users:manage', 'Manage users, assignments and access'),
('p0000000-0000-0000-0000-000000000002', 'administration', 'roles:manage', 'administration:roles:manage', 'Manage security roles and permission matrix'),
('p0000000-0000-0000-0000-000000000003', 'administration', 'settings:manage', 'administration:settings:manage', 'Manage branches, warehouses, taxes and company settings'),
('p0000000-0000-0000-0000-000000000004', 'reports', 'view', 'reports:view', 'Access financial, sales and inventory analytics'),
('p0000000-0000-0000-0000-000000000005', 'inventory', 'read', 'inventory:read', 'View catalog, products and stock levels'),
('p0000000-0000-0000-0000-000000000006', 'inventory', 'write', 'inventory:write', 'Create and edit products, categories, brands and units'),
('p0000000-0000-0000-0000-000000000007', 'inventory', 'adjust', 'inventory:adjust', 'Perform physical stock adjustments'),
('p0000000-0000-0000-0000-000000000008', 'inventory', 'transfer', 'inventory:transfer', 'Transfer inventory between branches and warehouses'),
('p0000000-0000-0000-0000-000000000009', 'purchasing', 'order', 'purchasing:order', 'Create and manage suppliers and purchase orders'),
('p0000000-0000-0000-0000-000000000010', 'purchasing', 'receipt', 'purchasing:receipt', 'Receive goods into warehouse inventory'),
('p0000000-0000-0000-0000-000000000011', 'sales', 'checkout', 'sales:checkout', 'Execute POS sales, invoices and payments'),
('p0000000-0000-0000-0000-000000000012', 'sales', 'session', 'sales:session', 'Open and close cashier shift sessions'),
('p0000000-0000-0000-0000-000000000013', 'sales', 'customers:manage', 'sales:customers:manage', 'Create and manage customer profiles'),
('p0000000-0000-0000-0000-000000000014', 'sales', 'receivables:read', 'sales:receivables:read', 'View accounts receivable, credit limits and customer debts');

-- 6. SEED THE 5 REQUESTED ROLES
INSERT INTO roles (id, company_id, code, name, description, is_system)
VALUES 
('r0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'ROLE_OWNER', 'Owner / Super Admin', 'Total access to all modules, users, roles, settings, reports, inventory and sales', TRUE),
('r0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'ROLE_ADMINISTRATOR', 'Administrator', 'Almost total access: inventory, sales, purchasing, reports, but WITHOUT user/role/settings management', TRUE),
('r0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'ROLE_SALESMAN_1', 'Salesman 1 (Senior)', 'Sales, POS checkout, customers, and EXTRA permission: accounts receivable / credit balance', TRUE),
('r0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000001', 'ROLE_SALESMAN_2', 'Salesman 2 (Standard)', 'Standard sales, POS checkout and customers, WITHOUT accounts receivable access', TRUE),
('r0000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000001', 'ROLE_STORE', 'Store Operator', 'Direct access to inventory and sales operations, WITHOUT reports or administration', TRUE);

-- 7. ASSIGN PERMISSIONS TO EACH ROLE

-- A. ROLE_OWNER: Acceso TOTAL a todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r0000000-0000-0000-0000-000000000001', id FROM permissions;

-- B. ROLE_ADMINISTRATOR: Acceso casi total (sin administration:users, roles, settings)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r0000000-0000-0000-0000-000000000002', id FROM permissions 
WHERE code NOT IN ('administration:users:manage', 'administration:roles:manage', 'administration:settings:manage');

-- C. ROLE_SALESMAN_1: Ventas + POS + Clientes + Cuentas por Cobrar (receivables:read) + Lectura Inventario
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r0000000-0000-0000-0000-000000000003', id FROM permissions 
WHERE code IN ('inventory:read', 'sales:checkout', 'sales:session', 'sales:customers:manage', 'sales:receivables:read');

-- D. ROLE_SALESMAN_2: Ventas normales + POS + Clientes + Lectura Inventario (SIN receivables:read)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r0000000-0000-0000-0000-000000000004', id FROM permissions 
WHERE code IN ('inventory:read', 'sales:checkout', 'sales:session', 'sales:customers:manage');

-- E. ROLE_STORE: Inventario completo + Recepci?n de compras + Ventas POS (SIN reportes ni administraci?n)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'r0000000-0000-0000-0000-000000000005', id FROM permissions 
WHERE code IN ('inventory:read', 'inventory:write', 'inventory:adjust', 'inventory:transfer', 'purchasing:receipt', 'sales:checkout', 'sales:session', 'sales:customers:manage');

-- 8. ASSIGN ROLES TO USERS
INSERT INTO user_branch_roles (id, user_id, branch_id, role_id, is_default)
VALUES 
('ub000000-0000-0000-0000-000000000001', 'u0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'r0000000-0000-0000-0000-000000000001', TRUE), -- owner
('ub000000-0000-0000-0000-000000000002', 'u0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000001', 'r0000000-0000-0000-0000-000000000002', TRUE), -- admin
('ub000000-0000-0000-0000-000000000003', 'u0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001', 'r0000000-0000-0000-0000-000000000003', TRUE), -- salesman1
('ub000000-0000-0000-0000-000000000004', 'u0000000-0000-0000-0000-000000000004', 'b0000000-0000-0000-0000-000000000001', 'r0000000-0000-0000-0000-000000000004', TRUE), -- salesman2
('ub000000-0000-0000-0000-000000000005', 'u0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000001', 'r0000000-0000-0000-0000-000000000005', TRUE); -- store

-- 9. SEED CATALOG & TAXES
INSERT INTO taxes (id, company_id, name, rate, is_default, is_active)
VALUES 
('t0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Standard VAT (18%)', 0.1800, TRUE, TRUE),
('t0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'Exempt / Zero VAT (0%)', 0.0000, FALSE, TRUE);

INSERT INTO units_of_measure (id, company_id, code, name, symbol, is_active)
VALUES 
('um000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'NIU', 'Unit / Piece', 'und', TRUE),
('um000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'KGM', 'Kilogram', 'kg', TRUE),
('um000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'LTR', 'Liter', 'L', TRUE),
('um000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000001', 'BOX', 'Package Box', 'box', TRUE);

INSERT INTO categories (id, company_id, code, name, description, is_active)
VALUES 
('cat00000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'CAT-ELEC', 'Electronics & Gadgets', 'Hardware, accessories and electronics', TRUE),
('cat00000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'CAT-OFF', 'Office Supplies', 'Desk organization and stationeries', TRUE);

INSERT INTO brands (id, company_id, name, description, is_active)
VALUES 
('br000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Logitech', 'Peripherals & accessories', TRUE),
('br000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'HP', 'Computing & Printers', TRUE),
('br000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'Samsung', 'Displays & Storage', TRUE);

-- 10. SEED PRODUCTS & INITIAL STOCK
INSERT INTO products (id, company_id, category_id, brand_id, unit_id, tax_id, sku, barcode, name, description, cost_price, selling_price, min_stock_alert, has_variants, is_active)
VALUES 
('prod0000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'cat00000-0000-0000-0000-000000000001', 'br000000-0000-0000-0000-000000000001', 'um000000-0000-0000-0000-000000000001', 't0000000-0000-0000-0000-000000000001', 'SKU-LOG-MX3S', '7611984022819', 'Logitech MX Master 3S Wireless Mouse', 'Ergonomic performance wireless mouse with 8K DPI sensor', 65.0000, 99.9900, 5.0000, FALSE, TRUE),
('prod0000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'cat00000-0000-0000-0000-000000000001', 'br000000-0000-0000-0000-000000000001', 'um000000-0000-0000-0000-000000000001', 't0000000-0000-0000-0000-000000000001', 'SKU-LOG-MXKEYS', '7611984022998', 'Logitech MX Keys Advanced Wireless Keyboard', 'Illuminated tactile keyboard with USB-C recharge', 75.0000, 119.5000, 5.0000, FALSE, TRUE),
('prod0000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'cat00000-0000-0000-0000-000000000001', 'br000000-0000-0000-0000-000000000003', 'um000000-0000-0000-0000-000000000001', 't0000000-0000-0000-0000-000000000001', 'SKU-SAM-T7-1TB', '8806090312489', 'Samsung T7 Portable SSD 1TB USB 3.2', 'High-speed external solid state drive', 70.0000, 105.0000, 3.0000, FALSE, TRUE),
('prod0000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000001', 'cat00000-0000-0000-0000-000000000002', 'br000000-0000-0000-0000-000000000002', 'um000000-0000-0000-0000-000000000004', 't0000000-0000-0000-0000-000000000001', 'SKU-HP-PAPER-A4', '194850123984', 'HP Premium A4 Copy Paper 500 Sheets (Box of 5)', 'Bright white multipurpose paper', 22.0000, 34.9000, 10.0000, FALSE, TRUE);

INSERT INTO stock_levels (id, warehouse_id, product_id, variant_id, quantity_available, quantity_reserved, quantity_in_transit)
VALUES 
('sl000000-0000-0000-0000-000000000001', 'w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000001', NULL, 45.0000, 0.0000, 0.0000),
('sl000000-0000-0000-0000-000000000002', 'w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000002', NULL, 30.0000, 0.0000, 0.0000),
('sl000000-0000-0000-0000-000000000003', 'w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000003', NULL, 18.0000, 0.0000, 0.0000),
('sl000000-0000-0000-0000-000000000004', 'w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000004', NULL, 50.0000, 0.0000, 0.0000);

-- Initial Kardex Entries
INSERT INTO stock_movements (warehouse_id, product_id, variant_id, movement_type, quantity, unit_cost, balance_after, reference_type, reference_id, notes, created_by)
VALUES 
('w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000001', NULL, 'ADJUSTMENT_POS', 45.0000, 65.0000, 45.0000, 'INITIAL_SEED', 'c0000000-0000-0000-0000-000000000001', 'Initial stock inventory', 'u0000000-0000-0000-0000-000000000001'),
('w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000002', NULL, 'ADJUSTMENT_POS', 30.0000, 75.0000, 30.0000, 'INITIAL_SEED', 'c0000000-0000-0000-0000-000000000001', 'Initial stock inventory', 'u0000000-0000-0000-0000-000000000001'),
('w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000003', NULL, 'ADJUSTMENT_POS', 18.0000, 70.0000, 18.0000, 'INITIAL_SEED', 'c0000000-0000-0000-0000-000000000001', 'Initial stock inventory', 'u0000000-0000-0000-0000-000000000001'),
('w0000000-0000-0000-0000-000000000001', 'prod0000-0000-0000-0000-000000000004', NULL, 'ADJUSTMENT_POS', 50.0000, 22.0000, 50.0000, 'INITIAL_SEED', 'c0000000-0000-0000-0000-000000000001', 'Initial stock inventory', 'u0000000-0000-0000-0000-000000000001');

-- 11. SEED SUPPLIERS & CUSTOMERS
INSERT INTO suppliers (id, company_id, tax_id, name, contact_name, email, phone, address, payment_terms_days, is_active)
VALUES 
('sup00000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'SUP-10293847-5', 'Tech Supplies Global Inc.', 'John Miller', 'sales@techsupplies.com', '+1-555-8822', '742 Evergreen Terrace, Springfield', 30, TRUE),
('sup00000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'SUP-55667788-9', 'Office Depot & Paper Corp', 'Maria Santos', 'orders@officedepotcorp.com', '+1-555-9933', '450 Industrial Blvd, Houston', 15, TRUE);

INSERT INTO customers (id, company_id, document_type, document_number, name, email, phone, address, credit_limit, current_credit, is_active)
VALUES 
('cust0000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'DNI', '00000000', 'Walk-in Customer / Final Consumer', 'pos@boxy.com', '+1-555-0000', 'Local Store', 0.0000, 0.0000, TRUE),
('cust0000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001', 'RUC', '20495839201', 'Innovatech Solutions SAC', 'procurement@innovatech.com', '+1-555-7744', 'Av. Las Begonias 441, San Isidro', 5000.0000, 1250.0000, TRUE);
