-- A payment recorded later against an existing sale (a collection on a credit sale) belongs to the
-- cash register open when the money came in, so the register's closing detail can list it. Payments
-- taken at checkout are not linked: they belong to the register of their invoice.
ALTER TABLE payments
    ADD COLUMN cashier_session_id BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_payments_cashier_session FOREIGN KEY (cashier_session_id) REFERENCES cashier_sessions(id);

CREATE INDEX idx_payments_cashier_session ON payments (cashier_session_id);
