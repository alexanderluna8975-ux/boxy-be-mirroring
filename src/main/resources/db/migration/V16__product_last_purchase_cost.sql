ALTER TABLE products
    ADD COLUMN last_purchase_cost DECIMAL(14, 4) NULL;

-- Backfill from the current weighted-average cost so existing products still get a sensible
-- default on their next purchase order line, instead of showing blank/zero until they're received again.
UPDATE products SET last_purchase_cost = cost_price WHERE last_purchase_cost IS NULL;
