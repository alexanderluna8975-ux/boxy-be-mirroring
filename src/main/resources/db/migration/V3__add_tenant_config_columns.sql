-- =============================================================================
-- V3__add_tenant_config_columns.sql: Multi-tenant and Business Adaptation Columns
-- Adds branding, theming, localization, feature flags, and terminology to companies
-- =============================================================================

ALTER TABLE companies
    ADD COLUMN trade_name VARCHAR(150) NULL AFTER name,
    ADD COLUMN slogan VARCHAR(255) NULL AFTER logo_url,
    ADD COLUMN primary_color VARCHAR(10) NOT NULL DEFAULT '#2563eb' AFTER slogan,
    ADD COLUMN primary_hover VARCHAR(10) NOT NULL DEFAULT '#1d4ed8' AFTER primary_color,
    ADD COLUMN primary_subtle_bg VARCHAR(10) NOT NULL DEFAULT '#eff6ff' AFTER primary_hover,
    ADD COLUMN tax_name VARCHAR(50) NOT NULL DEFAULT 'IVA' AFTER currency_symbol,
    ADD COLUMN default_tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 13.00 AFTER tax_name,
    ADD COLUMN tax_id_label VARCHAR(20) NOT NULL DEFAULT 'NIT' AFTER default_tax_rate,
    ADD COLUMN has_pos BOOLEAN NOT NULL DEFAULT TRUE AFTER allow_negative_stock,
    ADD COLUMN has_batches BOOLEAN NOT NULL DEFAULT FALSE AFTER has_pos,
    ADD COLUMN has_variants BOOLEAN NOT NULL DEFAULT FALSE AFTER has_batches,
    ADD COLUMN has_transfers BOOLEAN NOT NULL DEFAULT TRUE AFTER has_variants,
    ADD COLUMN has_purchasing BOOLEAN NOT NULL DEFAULT TRUE AFTER has_transfers,
    ADD COLUMN has_quotations BOOLEAN NOT NULL DEFAULT TRUE AFTER has_purchasing,
    ADD COLUMN has_multi_branch BOOLEAN NOT NULL DEFAULT TRUE AFTER has_quotations,
    ADD COLUMN unit_precision VARCHAR(20) NOT NULL DEFAULT 'integer' AFTER has_multi_branch,
    ADD COLUMN term_product VARCHAR(50) NOT NULL DEFAULT 'Producto' AFTER unit_precision,
    ADD COLUMN term_products VARCHAR(50) NOT NULL DEFAULT 'Productos' AFTER term_product,
    ADD COLUMN term_inventory VARCHAR(50) NOT NULL DEFAULT 'Inventario' AFTER term_products,
    ADD COLUMN term_customer VARCHAR(50) NOT NULL DEFAULT 'Cliente' AFTER term_inventory,
    ADD COLUMN term_pos VARCHAR(50) NOT NULL DEFAULT 'Punto de Venta' AFTER term_customer;
