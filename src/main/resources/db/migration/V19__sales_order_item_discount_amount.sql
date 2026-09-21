ALTER TABLE sales_order_items
    ADD COLUMN discount_amount DECIMAL(14, 4) NOT NULL DEFAULT 0;
