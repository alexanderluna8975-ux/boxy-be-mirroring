-- =============================================================================
-- V4__create_price_adjustments_tables.sql: Price Adjustments & Repricing History
-- Engine: InnoDB | Charset: utf8mb4 | Collation: utf8mb4_0900_ai_ci
-- =============================================================================

CREATE TABLE IF NOT EXISTS price_adjustments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT UNSIGNED NOT NULL,
    folio VARCHAR(50) NOT NULL UNIQUE,
    margin_percent DECIMAL(6, 2) NOT NULL,
    notes TEXT NULL,
    applied_by VARCHAR(100) NOT NULL,
    applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_pa_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS price_adjustment_lines (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    price_adjustment_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    purchase_price DECIMAL(14, 4) NOT NULL,
    previous_sale_price DECIMAL(14, 4) NOT NULL,
    new_sale_price DECIMAL(14, 4) NOT NULL,
    previous_margin_percent DECIMAL(6, 2) NULL,
    margin_percent DECIMAL(6, 2) NOT NULL,
    CONSTRAINT fk_pal_adjustment FOREIGN KEY (price_adjustment_id) REFERENCES price_adjustments(id) ON DELETE CASCADE,
    CONSTRAINT fk_pal_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
