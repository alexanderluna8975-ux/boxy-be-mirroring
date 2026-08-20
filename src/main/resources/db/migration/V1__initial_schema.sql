-- =============================================================================
-- V1__initial_schema.sql: Enterprise Multi-Branch Inventory & POS Database Schema
-- Engine: InnoDB | Charset: utf8mb4 | Collation: utf8mb4_0900_ai_ci
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. COMPANIES & MULTI-TENANCY
CREATE TABLE IF NOT EXISTS companies (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    logo_url VARCHAR(500),
    currency_code VARCHAR(10) NOT NULL DEFAULT 'USD',
    currency_symbol VARCHAR(5) NOT NULL DEFAULT '$',
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. BRANCHES & LOCATIONS
CREATE TABLE IF NOT EXISTS branches (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(50),
    email VARCHAR(100),
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_branches_company FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_branch_company_code (company_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3. WAREHOUSES
CREATE TABLE IF NOT EXISTS warehouses (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_warehouses_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    UNIQUE KEY uk_warehouse_branch_code (branch_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. USERS & AUTHENTICATION
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE, SUSPENDED
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 5. ROLES & PERMISSIONS (RBAC)
CREATE TABLE IF NOT EXISTS roles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_roles_company FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_roles_company_code (company_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS permissions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    module VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE, -- e.g. inventory:read, sales:checkout
    description VARCHAR(255),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_branch_roles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ubr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubr_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubr_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_branch (user_id, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. CATALOG: CATEGORIES, BRANDS, UNITS, TAXES
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_categories_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS brands (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_brands_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS units_of_measure (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    code VARCHAR(20) NOT NULL, -- e.g. NIU, KGM, LTR
    name VARCHAR(50) NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_units_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS taxes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    name VARCHAR(50) NOT NULL,
    rate DECIMAL(6, 4) NOT NULL, -- e.g. 0.1800 for 18%
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_taxes_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 7. PRODUCTS & VARIANTS
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(36) NULL,
    brand_id VARCHAR(36) NULL,
    unit_id VARCHAR(36) NOT NULL,
    tax_id VARCHAR(36) NULL,
    sku VARCHAR(100) NOT NULL,
    barcode VARCHAR(100),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cost_price DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    selling_price DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    min_stock_alert DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    has_variants BOOLEAN NOT NULL DEFAULT FALSE,
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by VARCHAR(36) NULL,
    updated_by VARCHAR(36) NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_products_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
    CONSTRAINT fk_products_unit FOREIGN KEY (unit_id) REFERENCES units_of_measure(id),
    CONSTRAINT fk_products_tax FOREIGN KEY (tax_id) REFERENCES taxes(id),
    UNIQUE KEY uk_products_company_sku (company_id, sku),
    INDEX idx_products_barcode (barcode),
    INDEX idx_products_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product_variants (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    barcode VARCHAR(100),
    attributes JSON NOT NULL, -- e.g. {"size": "L", "color": "Blue"}
    cost_adjustment DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    price_adjustment DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 8. INVENTORY: STOCK LEVELS & KARDEX MOVEMENTS
CREATE TABLE IF NOT EXISTS stock_levels (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    quantity_available DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    quantity_reserved DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    quantity_in_transit DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_stock_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_stock_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id),
    UNIQUE KEY uk_stock_location (warehouse_id, product_id, variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- IMMUTABLE APPEND-ONLY KARDEX
CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    warehouse_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    movement_type VARCHAR(30) NOT NULL, -- PURCHASE_IN, SALE_OUT, TRANSFER_IN, TRANSFER_OUT, ADJUSTMENT_ADD, ADJUSTMENT_SUB, RETURN_IN
    quantity DECIMAL(12, 4) NOT NULL,
    unit_cost DECIMAL(14, 4) NOT NULL,
    balance_after DECIMAL(12, 4) NOT NULL,
    reference_type VARCHAR(50) NOT NULL, -- INVOICE, PURCHASE_ORDER, TRANSFER, ADJUSTMENT
    reference_id VARCHAR(36) NOT NULL,
    notes VARCHAR(255),
    created_by VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_mov_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_mov_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_mov_user FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_mov_product_date (product_id, created_at),
    INDEX idx_mov_ref (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 9. STOCK TRANSFERS & ADJUSTMENTS
CREATE TABLE IF NOT EXISTS stock_transfers (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    source_warehouse_id VARCHAR(36) NOT NULL,
    destination_warehouse_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, REQUESTED, IN_TRANSIT, RECEIVED, REJECTED, CANCELLED
    notes VARCHAR(500),
    requested_by VARCHAR(36) NOT NULL,
    dispatched_by VARCHAR(36) NULL,
    received_by VARCHAR(36) NULL,
    dispatched_at DATETIME(6) NULL,
    received_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_st_source FOREIGN KEY (source_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_st_dest FOREIGN KEY (destination_warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_st_user FOREIGN KEY (requested_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    transfer_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    quantity_requested DECIMAL(12, 4) NOT NULL,
    quantity_received DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT fk_sti_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE,
    CONSTRAINT fk_sti_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stock_adjustments (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    adjustment_number VARCHAR(50) NOT NULL UNIQUE,
    reason VARCHAR(100) NOT NULL, -- PHYSICAL_COUNT, DAMAGE, EXPIRY, THEFT, OTHER
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_sa_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_sa_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stock_adjustment_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    adjustment_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    previous_quantity DECIMAL(12, 4) NOT NULL,
    new_quantity DECIMAL(12, 4) NOT NULL,
    difference_quantity DECIMAL(12, 4) NOT NULL,
    unit_cost DECIMAL(14, 4) NOT NULL,
    CONSTRAINT fk_sai_adjustment FOREIGN KEY (adjustment_id) REFERENCES stock_adjustments(id) ON DELETE CASCADE,
    CONSTRAINT fk_sai_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 10. PURCHASING: SUPPLIERS, ORDERS, RECEIPTS
CREATE TABLE IF NOT EXISTS suppliers (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    tax_id VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    payment_terms_days INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_suppliers_company FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_supplier_company_tax (company_id, tax_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS purchase_orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    supplier_id VARCHAR(36) NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    expected_delivery_date DATE,
    subtotal DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    tax_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT', -- DRAFT, ISSUED, APPROVED, PARTIALLY_RECEIVED, COMPLETED, CANCELLED
    notes VARCHAR(500),
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_po_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_po_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    purchase_order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    quantity_ordered DECIMAL(12, 4) NOT NULL,
    quantity_received DECIMAL(12, 4) NOT NULL DEFAULT 0.0000,
    unit_cost DECIMAL(14, 4) NOT NULL,
    tax_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.0000,
    total_cost DECIMAL(14, 4) NOT NULL,
    CONSTRAINT fk_poi_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS goods_receipts (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    purchase_order_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_invoice_number VARCHAR(50),
    received_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500),
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_gr_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_gr_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_gr_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS goods_receipt_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    goods_receipt_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    quantity_received DECIMAL(12, 4) NOT NULL,
    unit_cost DECIMAL(14, 4) NOT NULL,
    CONSTRAINT fk_gri_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_gri_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 11. SALES & POS: CUSTOMERS, SESSIONS, ORDERS, INVOICES, PAYMENTS
CREATE TABLE IF NOT EXISTS customers (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    document_type VARCHAR(20) NOT NULL, -- DNI, RUC, RFC, PASSPORT, OTHER
    document_number VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    credit_limit DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    current_credit DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_customers_company FOREIGN KEY (company_id) REFERENCES companies(id),
    UNIQUE KEY uk_customer_company_doc (company_id, document_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS cashier_sessions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    branch_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    closed_at DATETIME(6) NULL,
    initial_cash DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    expected_cash DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    actual_cash DECIMAL(14, 4) NULL,
    difference DECIMAL(14, 4) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, CLOSED
    notes VARCHAR(500),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cs_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_cs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sales_orders (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    order_type VARCHAR(20) NOT NULL DEFAULT 'SALE', -- QUOTATION, SALE
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED', -- QUOTATION, CONFIRMED, PAID, CANCELLED
    subtotal DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    discount_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    tax_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    notes VARCHAR(500),
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_so_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_so_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_so_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sales_order_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    sales_order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    quantity DECIMAL(12, 4) NOT NULL,
    unit_price DECIMAL(14, 4) NOT NULL,
    discount_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.0000,
    tax_rate DECIMAL(6, 4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(14, 4) NOT NULL,
    CONSTRAINT fk_soi_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_soi_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS invoices (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    branch_id VARCHAR(36) NOT NULL,
    warehouse_id VARCHAR(36) NOT NULL,
    cashier_session_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    sales_order_id VARCHAR(36) NULL,
    document_type VARCHAR(20) NOT NULL, -- INVOICE, TICKET, RECEIPT
    series VARCHAR(10) NOT NULL,
    number VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NULL UNIQUE,
    subtotal DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    discount_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    tax_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED', -- ISSUED, VOIDED
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inv_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_inv_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_inv_session FOREIGN KEY (cashier_session_id) REFERENCES cashier_sessions(id),
    CONSTRAINT fk_inv_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_inv_user FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_invoice_series_number (branch_id, document_type, series, number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS invoice_items (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(36) NULL,
    product_name VARCHAR(200) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity DECIMAL(12, 4) NOT NULL,
    unit_price DECIMAL(14, 4) NOT NULL,
    unit_cost DECIMAL(14, 4) NOT NULL,
    discount_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    tax_amount DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    total_amount DECIMAL(14, 4) NOT NULL,
    CONSTRAINT fk_ii_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_ii_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    payment_method VARCHAR(30) NOT NULL, -- CASH, CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, STORE_CREDIT
    amount DECIMAL(14, 4) NOT NULL,
    reference_code VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_pay_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS credit_notes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    series VARCHAR(10) NOT NULL,
    number VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    amount DECIMAL(14, 4) NOT NULL,
    restock_items BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_cn_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_cn_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 12. AUDIT LOGS (APPEND-ONLY)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NULL,
    action VARCHAR(50) NOT NULL, -- CREATE, UPDATE, DELETE, LOGIN, CHECKOUT
    resource_type VARCHAR(50) NOT NULL, -- PRODUCT, INVOICE, USER, SETTING
    resource_id VARCHAR(36) NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    details JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_resource (resource_type, resource_id),
    INDEX idx_audit_date (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
