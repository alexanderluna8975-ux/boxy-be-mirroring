-- V10__accounts_receivable.sql
-- Cuentas por Cobrar: an invoice now records the payment method the SALE
-- itself was made under (cash/card/transfer/credit), independent of any
-- later `payments` settlement rows, plus the credit term and computed due
-- date a credit sale needs.

ALTER TABLE invoices
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'cash',
    ADD COLUMN credit_term_days INT NULL,
    ADD COLUMN due_date DATETIME(6) NULL;

-- Backfill: an invoice's own payment method was never tracked before this
-- migration. Every pre-existing invoice was fully settled at checkout (the
-- old code path always wrote one full-amount payment), so its first payment
-- row is a faithful stand-in for how the sale was made.
UPDATE invoices i
SET payment_method = LOWER(COALESCE(
    (SELECT p.payment_method FROM payments p WHERE p.invoice_id = i.id ORDER BY p.id LIMIT 1),
    'cash'
));
