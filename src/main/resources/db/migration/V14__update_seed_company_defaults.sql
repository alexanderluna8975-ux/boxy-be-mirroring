-- The company header block on printed documents (Cotización, Nota de Venta, Nota de
-- Transferencia, Orden de Compra, Recepción) shows address and a city/country line separately —
-- `companies` only had one free-text `address` column, so a `city` column is added for the second
-- line ("Cochabamba - Bolivia"), distinct from the office/branch address ("Central").
ALTER TABLE companies
    ADD COLUMN city VARCHAR(100) NULL AFTER address;

-- Sets the seeded tenant's real identity/contact defaults (was placeholder "Boxy Enterprise Corp"
-- data from V2's seed). These are the actual values printed on every generated PDF.
UPDATE companies
SET name = 'LATINATOOLS',
    trade_name = 'LATINATOOLS',
    address = 'Central',
    city = 'Cochabamba - Bolivia',
    phone = '71706123'
WHERE id = 1;
