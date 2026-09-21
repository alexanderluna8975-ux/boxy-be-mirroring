-- `CreateQuotationRequest.validUntil` has always been accepted by the create/update endpoints
-- but was silently discarded — no column existed to persist it, so `QuotationDto.validUntil`
-- (already declared on the DTO) was never actually populated. Needed to print "Validez: Hasta
-- {date} ({N} días)" on the Nota de Cotización PDF.
ALTER TABLE sales_orders
    ADD COLUMN valid_until DATETIME(6) NULL;
