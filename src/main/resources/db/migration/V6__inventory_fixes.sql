-- =============================================================================
-- V6__inventory_fixes.sql: Inventory module corrections
--   1. stock_transfers: record who/when approved, and FK the audit columns
--      that were left as bare BIGINTs.
--   2. Missing indexes for the inventory list/filter endpoints.
-- =============================================================================

ALTER TABLE stock_transfers
    ADD COLUMN approved_by BIGINT UNSIGNED NULL AFTER requested_by,
    ADD COLUMN approved_at DATETIME(6) NULL AFTER dispatched_at;

-- dispatched_by / received_by existieron desde V1 como BIGINT sueltos, sin FK.
-- Anular referencias huerfanas antes de crear la constraint, o el ALTER falla
-- con errno 1452 y deja la migracion en estado 'failed'.
UPDATE stock_transfers st
   SET st.dispatched_by = NULL
 WHERE st.dispatched_by IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = st.dispatched_by);

UPDATE stock_transfers st
   SET st.received_by = NULL
 WHERE st.received_by IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = st.received_by);

ALTER TABLE stock_transfers
    ADD CONSTRAINT fk_st_approved_by FOREIGN KEY (approved_by) REFERENCES users(id),
    ADD CONSTRAINT fk_st_dispatched_by FOREIGN KEY (dispatched_by) REFERENCES users(id),
    ADD CONSTRAINT fk_st_received_by FOREIGN KEY (received_by) REFERENCES users(id);

CREATE INDEX idx_mov_warehouse_date ON stock_movements (warehouse_id, created_at);
CREATE INDEX idx_mov_created_at ON stock_movements (created_at);
CREATE INDEX idx_st_status ON stock_transfers (status);
CREATE INDEX idx_st_created_at ON stock_transfers (created_at);
CREATE INDEX idx_sa_created_at ON stock_adjustments (created_at);
