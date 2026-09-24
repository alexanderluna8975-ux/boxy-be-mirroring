-- Cash "redondeo" — a small ± correction applied to an invoice's total at checkout,
-- entirely independent of discount_amount (never a business discount, purely a
-- convenience for rounding to a cash-friendly figure), so financial reports keep
-- reading discount_amount as genuine discounts only.
ALTER TABLE invoices
    ADD COLUMN rounding_adjustment DECIMAL(14, 4) NOT NULL DEFAULT 0;
