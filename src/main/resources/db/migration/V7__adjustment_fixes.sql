-- =============================================================================
-- V7__adjustment_fixes.sql: Stock adjustment corrections
--   1. Record who/when approved, matching stock_transfers' approved_by/approved_at.
--   2. Normalize existing status values to the UPPER_SNAKE vocabulary the
--      application now writes (was a mix of the FE's kebab-case and the
--      original 'COMPLETED' default).
-- =============================================================================

ALTER TABLE stock_adjustments
    ADD COLUMN approved_by BIGINT UNSIGNED NULL AFTER created_by,
    ADD COLUMN approved_at DATETIME(6) NULL AFTER approved_by;

ALTER TABLE stock_adjustments
    ADD CONSTRAINT fk_sa_approved_by FOREIGN KEY (approved_by) REFERENCES users(id);

UPDATE stock_adjustments SET status = 'PENDING_APPROVAL' WHERE status IN ('pending-approval', 'PENDING-APPROVAL');
UPDATE stock_adjustments SET status = 'APPROVED' WHERE status IN ('approved', 'COMPLETED', 'completed');
UPDATE stock_adjustments SET status = 'REJECTED' WHERE status IN ('rejected');
UPDATE stock_adjustments SET status = 'CANCELLED' WHERE status IN ('cancelled');

ALTER TABLE stock_adjustments
    ALTER COLUMN status SET DEFAULT 'PENDING_APPROVAL';
