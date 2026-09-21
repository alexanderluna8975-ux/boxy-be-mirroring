-- =============================================================================
-- V15__product_cost_history.sql: Append-only cost history, one row per product
-- per goods receipt, recording the weighted-average cost recalculation that
-- receipt triggered. `goods_receipt_id`/`goods_receipt_number` are a plain
-- cross-module reference (FK enforced here, no JPA relation) — same
-- convention `stock_movements.reference_id` already uses, so the catalog
-- module doesn't have to depend on the purchasing module.
-- Engine: InnoDB | Charset: utf8mb4 | Collation: utf8mb4_unicode_ci
-- =============================================================================

CREATE TABLE IF NOT EXISTS product_cost_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    previous_cost DECIMAL(14, 4) NOT NULL,
    new_cost DECIMAL(14, 4) NOT NULL,
    unit_cost DECIMAL(14, 4) NOT NULL,
    quantity_received DECIMAL(12, 4) NOT NULL,
    goods_receipt_id BIGINT UNSIGNED NOT NULL,
    goods_receipt_number VARCHAR(50) NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_pch_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_pch_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_pch_user FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_pch_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
