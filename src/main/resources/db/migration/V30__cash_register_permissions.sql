-- Cajas (cash registers) become their own permission module, so an administrator can decide per role
-- (and per user, through overrides) who may see the registers, open one and close one — instead of
-- everything hanging off pos:create.
--   cash-registers:view   ver el historial y el detalle de las cajas
--   cash-registers:create abrir caja
--   cash-registers:update cerrar caja
INSERT INTO permissions (module, action, code, description) VALUES
('cash-registers','view','cash-registers:view','Ver las cajas, su historial y el detalle de operaciones'),
('cash-registers','create','cash-registers:create','Abrir caja'),
('cash-registers','update','cash-registers:update','Cerrar caja')
ON DUPLICATE KEY UPDATE
    module      = VALUES(module),
    action      = VALUES(action),
    description = VALUES(description);

-- Nobody loses what they could already do: every role that could operate the POS (pos:create) gets
-- the three; a role that could only view sales (pos:view) gets to see the registers.
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions op ON op.id = rp.permission_id AND op.code = 'pos:create'
  JOIN permissions np ON np.code IN ('cash-registers:view', 'cash-registers:create', 'cash-registers:update')
 WHERE NOT EXISTS (
     SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id
 );

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, np.id
  FROM role_permissions rp
  JOIN permissions op ON op.id = rp.permission_id AND op.code = 'pos:view'
  JOIN permissions np ON np.code = 'cash-registers:view'
 WHERE NOT EXISTS (
     SELECT 1 FROM role_permissions x WHERE x.role_id = rp.role_id AND x.permission_id = np.id
 );

-- The super administrator holds every permission in the catalog.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.code = 'ROLE_SUPER_ADMIN'
   AND p.code IN ('cash-registers:view', 'cash-registers:create', 'cash-registers:update')
   AND NOT EXISTS (
       SELECT 1 FROM role_permissions x WHERE x.role_id = r.id AND x.permission_id = p.id
   );
