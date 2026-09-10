-- =============================================================================
-- V9__supplier_tax_id_optional.sql
-- The supplier form dropped the RFC field, so tax_id is no longer captured on
-- create. Make the column nullable; the UNIQUE key (company_id, tax_id) stays —
-- MariaDB/MySQL allow multiple NULLs in a unique index, so RFC-less suppliers
-- coexist without collisions.
-- =============================================================================

ALTER TABLE suppliers MODIFY tax_id VARCHAR(50) NULL;
