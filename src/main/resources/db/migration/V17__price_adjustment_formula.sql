ALTER TABLE price_adjustments
    ADD COLUMN tariff VARCHAR(20) NOT NULL DEFAULT 'increase' AFTER folio,
    ADD COLUMN unit VARCHAR(20) NOT NULL DEFAULT 'percent' AFTER tariff,
    ADD COLUMN amount DECIMAL(14, 4) NOT NULL DEFAULT 0 AFTER unit,
    ADD COLUMN based_on VARCHAR(20) NOT NULL DEFAULT 'cost' AFTER amount,
    ADD COLUMN rounding_mode VARCHAR(20) NOT NULL DEFAULT 'none' AFTER based_on;

-- Backfill existing rows: the old model was always "increase, percent, based on cost, no rounding".
UPDATE price_adjustments SET amount = margin_percent;

ALTER TABLE price_adjustments DROP COLUMN margin_percent;

ALTER TABLE price_adjustment_lines
    CHANGE COLUMN margin_percent new_margin_percent DECIMAL(6, 2) NULL,
    ADD COLUMN overridden BOOLEAN NOT NULL DEFAULT FALSE AFTER new_margin_percent;
