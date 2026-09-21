-- V11__add_cajera_role.sql
-- Creacion del nuevo rol 'Cajera' con permisos predeterminados:
-- Dashboard: Leer
-- Punto de Venta: Crear, Leer, Actualizar, Eliminar
-- Clientes: Leer
-- Cotizaciones: Leer
-- Reportes: Leer
-- Resto de modulos sin permisos asignados por defecto.

-- 1. Insertar el rol Cajera en cada empresa existente si aun no existe
INSERT INTO roles (company_id, code, name, description, is_system)
SELECT c.id,
       'ROLE_CASHIER',
       'Cajera',
       'Operaciones de punto de venta, turnos de caja, clientes, cotizaciones y reportes',
       FALSE
  FROM companies c
 WHERE NOT EXISTS (
     SELECT 1
       FROM roles r
      WHERE r.company_id = c.id
        AND (r.code = 'ROLE_CASHIER' OR r.name = 'Cajera')
 );

-- 2. Asignar los permisos iniciales a cada rol ROLE_CASHIER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code IN (
      'dashboard:view',
      'pos:view', 'pos:create', 'pos:update', 'pos:delete',
      'customers:view',
      'quotations:view',
      'reports:view'
  )
 WHERE r.code = 'ROLE_CASHIER'
   AND NOT EXISTS (
       SELECT 1
         FROM role_permissions rp
        WHERE rp.role_id = r.id
          AND rp.permission_id = p.id
   );
