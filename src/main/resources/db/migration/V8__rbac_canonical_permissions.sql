-- =============================================================================
-- V8__rbac_canonical_permissions.sql
-- Unifica el vocabulario de permisos: <ModuleKey>:<PermissionLevel>, el mismo
-- que ya usa el frontend para gatear rutas y menús. El backend pasa a ser la
-- fuente de verdad: el FE deja de derivar permisos de una tabla local y
-- consume el array `permissions` que ya devuelve el login/`/auth/me`.
--
-- Idempotente (ON DUPLICATE KEY UPDATE por `code` UNIQUE). No toca `roles` ni
-- `user_branch_roles`: el superadmin (user_id=1, role_id=1) queda intacto.
--
-- Los 4 códigos legacy que SÍ se comprueban hoy en @PreAuthorize
-- (inventory:read|write|adjust|transfer) se conservan como alias marcados
-- [DEPRECATED] durante un release y se retiran en V9, una vez las
-- anotaciones usen solo el código canónico. Los otros 10 códigos legacy no
-- los comprueba nada y se eliminan directamente (role_permissions.permission_id
-- tiene FK ON DELETE CASCADE, así que las filas puente se limpian solas).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. UPSERT del catálogo canónico (55 códigos).
--    'reports:view' ya existe con ese mismo código en ambos vocabularios, así
--    que se actualiza en sitio y conserva su id (y por tanto sus grants).
-- -----------------------------------------------------------------------------
INSERT INTO permissions (module, action, code, description) VALUES
('dashboard','view','dashboard:view','Ver el panel de indicadores'),

('products','view','products:view','Ver el catálogo de productos'),
('products','create','products:create','Crear productos, categorías, marcas y unidades'),
('products','update','products:update','Editar productos, precios y costos'),
('products','delete','products:delete','Eliminar o desactivar productos'),

('inventory','view','inventory:view','Ver existencias, almacenes y kardex'),
('inventory','create','inventory:create','Crear almacenes y ajustes de inventario'),
('inventory','update','inventory:update','Editar existencias y ajustes de inventario'),
('inventory','delete','inventory:delete','Eliminar almacenes y ajustes en borrador'),
('inventory','approve','inventory:approve','Aprobar ajustes de inventario'),

('purchases','view','purchases:view','Ver pedidos de compra'),
('purchases','create','purchases:create','Crear pedidos de compra'),
('purchases','update','purchases:update','Editar pedidos de compra'),
('purchases','delete','purchases:delete','Cancelar o eliminar pedidos de compra'),
('purchases','approve','purchases:approve','Aprobar pedidos de compra'),
('purchases','receive','purchases:receive','Registrar la recepción física de mercancía'),

('suppliers','view','suppliers:view','Ver proveedores'),
('suppliers','create','suppliers:create','Crear proveedores'),
('suppliers','update','suppliers:update','Editar proveedores'),
('suppliers','delete','suppliers:delete','Eliminar o desactivar proveedores'),

('customers','view','customers:view','Ver clientes, crédito y cuentas por cobrar'),
('customers','create','customers:create','Crear clientes'),
('customers','update','customers:update','Editar clientes y límites de crédito'),
('customers','delete','customers:delete','Eliminar o desactivar clientes'),

('quotations','view','quotations:view','Ver cotizaciones'),
('quotations','create','quotations:create','Crear cotizaciones'),
('quotations','update','quotations:update','Editar cotizaciones'),
('quotations','delete','quotations:delete','Anular o eliminar cotizaciones'),

('pos','view','pos:view','Ver ventas y documentos del punto de venta'),
('pos','create','pos:create','Operar el POS: cobrar y abrir/cerrar turno de caja'),
('pos','update','pos:update','Corregir ventas y cierres de caja'),
('pos','delete','pos:delete','Anular ventas'),

('transfers','view','transfers:view','Ver transferencias entre almacenes'),
('transfers','create','transfers:create','Solicitar transferencias entre almacenes'),
('transfers','update','transfers:update','Editar y despachar transferencias'),
('transfers','delete','transfers:delete','Cancelar transferencias'),
('transfers','approve','transfers:approve','Autorizar transferencias'),
('transfers','receive','transfers:receive','Recibir transferencias en destino'),

('reports','view','reports:view','Ver reportes financieros, de ventas e inventario'),
('reports','create','reports:create','Crear y exportar reportes personalizados'),
('reports','update','reports:update','Editar reportes guardados'),
('reports','delete','reports:delete','Eliminar reportes guardados'),

('settings','view','settings:view','Ver la configuración de empresa, sucursales e impuestos'),
('settings','create','settings:create','Crear sucursales, almacenes e impuestos'),
('settings','update','settings:update','Editar la configuración general'),
('settings','delete','settings:delete','Eliminar sucursales, almacenes e impuestos'),

('roles','view','roles:view','Ver roles y la matriz de permisos'),
('roles','create','roles:create','Crear roles'),
('roles','update','roles:update','Editar roles y su matriz de permisos'),
('roles','delete','roles:delete','Eliminar roles'),

('users','view','users:view','Ver usuarios y sus asignaciones'),
('users','create','users:create','Crear usuarios'),
('users','update','users:update','Editar usuarios, sucursales y roles asignados'),
('users','delete','users:delete','Eliminar o desactivar usuarios'),

('audit-logs','view','audit-logs:view','Ver los registros de auditoría')
ON DUPLICATE KEY UPDATE
    module      = VALUES(module),
    action      = VALUES(action),
    description = VALUES(description);

-- -----------------------------------------------------------------------------
-- 2. Marcar como [DEPRECATED] los 4 códigos legacy que sí se comprueban hoy.
--    Se eliminan en V9, cuando @PreAuthorize ya solo use el código canónico.
-- -----------------------------------------------------------------------------
UPDATE permissions
   SET description = CONCAT('[DEPRECATED - eliminar en V9] ', description)
 WHERE code IN ('inventory:read','inventory:write','inventory:adjust','inventory:transfer')
   AND description NOT LIKE '[DEPRECATED%';

-- -----------------------------------------------------------------------------
-- 3. Reconstruir role_permissions para los 4 roles del sistema.
--    Derivado de MANUAL_USUARIO.md, con segregación de funciones entre
--    Administrador (ordena/aprueba) y Almacén (crea/recibe) en compras y
--    transferencias, y sin acceso de Almacén a la aprobación de sus propios
--    ajustes de inventario.
-- -----------------------------------------------------------------------------
DELETE rp
  FROM role_permissions rp
  JOIN roles r ON r.id = rp.role_id
 WHERE r.code IN ('ROLE_SUPER_ADMIN','ROLE_ADMINISTRATOR','ROLE_SALESMAN','ROLE_STORE');

-- 3.A Super Administrador: acceso total e irrestricto (incluye los alias legacy).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.code = 'ROLE_SUPER_ADMIN';

-- 3.B Administrador: compras, ventas, inventario, catálogos, proveedores,
--     clientes y consulta de reportes. Sin usuarios/roles/configuración
--     (el manual reserva "dar de alta usuarios" al Super Admin).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
    'dashboard:view',
    'products:view','products:create','products:update','products:delete',
    'inventory:view','inventory:create','inventory:update','inventory:delete','inventory:approve',
    'purchases:view','purchases:create','purchases:update','purchases:delete','purchases:approve',
    'suppliers:view','suppliers:create','suppliers:update','suppliers:delete',
    'customers:view','customers:create','customers:update','customers:delete',
    'quotations:view','quotations:create','quotations:update','quotations:delete',
    'pos:view','pos:create','pos:update','pos:delete',
    'transfers:view','transfers:approve',
    'reports:view',
    'inventory:read','inventory:write','inventory:adjust'
  )
 WHERE r.code = 'ROLE_ADMINISTRATOR';

-- 3.C Vendedor: POS, turno de caja, clientes y cotizaciones. Sin reportes
--     (MANUAL_USUARIO.md: "no ve costos de compra ni márgenes de utilidad").
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
    'dashboard:view',
    'products:view',
    'inventory:view',
    'customers:view','customers:create','customers:update','customers:delete',
    'quotations:view','quotations:create','quotations:update','quotations:delete',
    'pos:create',
    'inventory:read'
  )
 WHERE r.code = 'ROLE_SALESMAN';

-- 3.D Almacén: existencias, ajustes, transferencias y recepción de compras.
--     Sin inventory:approve: otro rol aprueba sus ajustes (segregación).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
    'dashboard:view',
    'products:view',
    'inventory:view','inventory:create','inventory:update','inventory:delete',
    'purchases:view','purchases:receive',
    'suppliers:view',
    'transfers:view','transfers:create','transfers:update','transfers:delete','transfers:receive',
    'reports:view',
    'inventory:read','inventory:write','inventory:adjust','inventory:transfer'
  )
 WHERE r.code = 'ROLE_STORE';

-- -----------------------------------------------------------------------------
-- 4. Eliminar los 10 códigos legacy que ningún @PreAuthorize comprueba.
--    'reports:view' no se toca: es canónico.
-- -----------------------------------------------------------------------------
DELETE FROM permissions WHERE code IN (
    'administration:users:manage',
    'administration:roles:manage',
    'administration:settings:manage',
    'administration:manage',
    'purchasing:order',
    'purchasing:receipt',
    'sales:checkout',
    'sales:session',
    'sales:customers:manage',
    'sales:receivables:read'
);
