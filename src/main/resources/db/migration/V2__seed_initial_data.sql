-- =============================================================================
-- V2__seed_initial_data.sql: Clean Initial Seed for Boxy
-- Auto-increment numeric IDs starting at 1
-- Single initial user: Super admin (secure credentials)
-- 4 System Roles: Super admin, Administrador, Vendedor, Almacén
-- Zero dummy / test business data
-- =============================================================================

-- 1. SEED INITIAL COMPANY
INSERT INTO companies (id, name, tax_id, email, phone, address, currency_code, currency_symbol, timezone, allow_negative_stock, is_active)
VALUES (1, 'Boxy Enterprise Corp', 'TAX-00000001-1', 'admin@boxy.com', '+1-555-0100', '100 Main Street, Suite 100', 'USD', '$', 'UTC', FALSE, TRUE);

-- 2. SEED INITIAL MAIN BRANCH
INSERT INTO branches (id, company_id, code, name, address, phone, email, is_main, is_active)
VALUES (1, 1, 'BR-MAIN', 'Sucursal Central', 'Av. Central 100, Sede Principal', '+1-555-0101', 'central@boxy.com', TRUE, TRUE);

-- 3. SEED INITIAL DEFAULT WAREHOUSE
INSERT INTO warehouses (id, branch_id, code, name, is_default, is_active)
VALUES (1, 1, 'WH-MAIN', 'Almacén Central', TRUE, TRUE);

-- 4. SEED SOLE INITIAL USER: Super admin
-- Plain Password: SuperAdmin#2026!Secured$
-- BCrypt Hash: $2a$10$h0Is2./h5f3A2qlm33GhgOQmD17BaCIJLQ3.T5ptXdfwsnFr9L3gi
INSERT INTO users (id, company_id, username, email, password_hash, first_name, last_name, status)
VALUES (1, 1, 'superadmin', 'superadmin@boxy.com', '$2a$10$h0Is2./h5f3A2qlm33GhgOQmD17BaCIJLQ3.T5ptXdfwsnFr9L3gi', 'Super', 'Admin', 'ACTIVE');

-- 5. SEED SYSTEM PERMISSIONS
INSERT INTO permissions (id, module, action, code, description)
VALUES 
(1, 'administration', 'users:manage', 'administration:users:manage', 'Manage users, assignments and access'),
(2, 'administration', 'roles:manage', 'administration:roles:manage', 'Manage security roles and permission matrix'),
(3, 'administration', 'settings:manage', 'administration:settings:manage', 'Manage branches, warehouses, taxes and company settings'),
(4, 'reports', 'view', 'reports:view', 'Access financial, sales and inventory analytics'),
(5, 'inventory', 'read', 'inventory:read', 'View catalog, products and stock levels'),
(6, 'inventory', 'write', 'inventory:write', 'Create and edit products, categories, brands and units'),
(7, 'inventory', 'adjust', 'inventory:adjust', 'Perform physical stock adjustments'),
(8, 'inventory', 'transfer', 'inventory:transfer', 'Transfer inventory between branches and warehouses'),
(9, 'purchasing', 'order', 'purchasing:order', 'Create and manage suppliers and purchase orders'),
(10, 'purchasing', 'receipt', 'purchasing:receipt', 'Receive goods into warehouse inventory'),
(11, 'sales', 'checkout', 'sales:checkout', 'Execute POS sales, invoices and payments'),
(12, 'sales', 'session', 'sales:session', 'Open and close cashier shift sessions'),
(13, 'sales', 'customers:manage', 'sales:customers:manage', 'Create and manage customer profiles'),
(14, 'sales', 'receivables:read', 'sales:receivables:read', 'View accounts receivable, credit limits and customer debts'),
(15, 'administration', 'manage', 'administration:manage', 'Global administration authority');

-- 6. SEED THE 4 REQUESTED SYSTEM ROLES
-- 1. Super admin
-- 2. Administrador
-- 3. Vendedor
-- 4. Almacén
INSERT INTO roles (id, company_id, code, name, description, is_system)
VALUES 
(1, 1, 'ROLE_SUPER_ADMIN', 'Super admin', 'Acceso total e irrestricto al sistema, usuarios, roles, configuraciones y módulos', TRUE),
(2, 1, 'ROLE_ADMINISTRATOR', 'Administrador', 'Acceso operativo amplio a inventarios, compras, ventas, POS y reportes', TRUE),
(3, 1, 'ROLE_SALESMAN', 'Vendedor', 'Operaciones comerciales, turnos de caja, clientes y ventas POS', TRUE),
(4, 1, 'ROLE_STORE', 'Almacén', 'Operaciones de inventario, ajustes de stock, transferencias y recepción de compras', TRUE);

-- 7. ASSIGN PERMISSIONS TO THE 4 ROLES

-- A. Super admin (id=1): Acceso TOTAL a todos los permisos
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

-- B. Administrador (id=2): Acceso operativo completo (excluyendo gestión de usuarios y roles del sistema)
INSERT INTO role_permissions (role_id, permission_id)
SELECT 2, id FROM permissions 
WHERE code NOT IN ('administration:users:manage', 'administration:roles:manage', 'administration:settings:manage', 'administration:manage');

-- C. Vendedor (id=3): Ventas, POS, turnos de caja, clientes y lectura de inventario
INSERT INTO role_permissions (role_id, permission_id)
SELECT 3, id FROM permissions 
WHERE code IN ('inventory:read', 'sales:checkout', 'sales:session', 'sales:customers:manage', 'sales:receivables:read');

-- D. Almacén (id=4): Control de inventario, ajustes, transferencias y recepción de compras
INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, id FROM permissions 
WHERE code IN ('inventory:read', 'inventory:write', 'inventory:adjust', 'inventory:transfer', 'purchasing:receipt');

-- 8. ASSIGN INITIAL USER TO ROLE "Super admin"
INSERT INTO user_branch_roles (id, user_id, branch_id, role_id, is_default)
VALUES 
(1, 1, 1, 1, TRUE);

-- 9. SEED BASE SYSTEM METADATA (Taxes & Units of Measure)
INSERT INTO taxes (id, company_id, name, rate, is_default, is_active)
VALUES 
(1, 1, 'Standard VAT (18%)', 0.1800, TRUE, TRUE),
(2, 1, 'Exempt / Zero VAT (0%)', 0.0000, FALSE, TRUE);

INSERT INTO units_of_measure (id, company_id, code, name, symbol, is_active)
VALUES 
(1, 1, 'NIU', 'Unidad', 'und', TRUE),
(2, 1, 'KGM', 'Kilogramo', 'kg', TRUE),
(3, 1, 'LTR', 'Litro', 'L', TRUE),
(4, 1, 'BOX', 'Caja', 'caja', TRUE);
