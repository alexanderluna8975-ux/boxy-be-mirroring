-- Voiding a sale now records why, when and by whom (the reason is required by the API). Until now the
-- sale detail showed the invoice's creation time and a hard-coded "Admin" as when/who voided it.
-- Columns are nullable: sales voided before this migration have no reason on record.
ALTER TABLE invoices
    ADD COLUMN void_reason VARCHAR(500) NULL,
    ADD COLUMN voided_at DATETIME(6) NULL,
    ADD COLUMN voided_by BIGINT UNSIGNED NULL,
    ADD CONSTRAINT fk_invoices_voided_by FOREIGN KEY (voided_by) REFERENCES users(id);
