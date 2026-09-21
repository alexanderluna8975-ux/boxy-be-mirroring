-- Correlative folio numbering. Replaces per-service ad hoc folio generation (UUID fragments for
-- adjustments/transfers, System.currentTimeMillis() for sales/quotations/purchase orders/goods
-- receipts, a race-prone count()+1 for price adjustments) with one shared, atomic, per-company
-- counter per document type. See DocumentSequenceService.

CREATE TABLE IF NOT EXISTS document_sequences (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    `last_value` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_document_sequences_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uk_document_sequence UNIQUE (company_id, document_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed every company's counters from its highest EXISTING correlative-looking folio, so upgrading
-- doesn't restart numbering at ADJ-00001 and collide with real historical documents. Only the
-- price-adjustment folio was ever actually sequential (`PR-%05d`); the rest were UUID/timestamp
-- based, so their MAX(...) below is 0 for every company — this is expected, not a bug: the first
-- adjustment/transfer/sale/quotation/purchase-order/goods-receipt created after this migration
-- legitimately starts the real correlative sequence at 1.

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT company_id, 'ADJUSTMENT', COALESCE(MAX(CAST(SUBSTRING(adjustment_number, 5) AS UNSIGNED)), 0)
FROM stock_adjustments
WHERE adjustment_number REGEXP '^ADJ-[0-9]+$'
GROUP BY company_id;

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT company_id, 'TRANSFER', COALESCE(MAX(CAST(SUBSTRING(transfer_number, 5) AS UNSIGNED)), 0)
FROM stock_transfers
WHERE transfer_number REGEXP '^TRF-[0-9]+$'
GROUP BY company_id;

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT company_id, 'QUOTATION', COALESCE(MAX(CAST(SUBSTRING(order_number, 5) AS UNSIGNED)), 0)
FROM sales_orders
WHERE order_type = 'QUOTATION' AND order_number REGEXP '^COT-[0-9]+$'
GROUP BY company_id;

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT company_id, 'PRICE_ADJUSTMENT', COALESCE(MAX(CAST(SUBSTRING(folio, 4) AS UNSIGNED)), 0)
FROM price_adjustments
WHERE folio REGEXP '^PR-[0-9]+$'
GROUP BY company_id;

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT company_id, 'PURCHASE_ORDER', COALESCE(MAX(CAST(SUBSTRING(order_number, 4) AS UNSIGNED)), 0)
FROM purchase_orders
WHERE order_number REGEXP '^PO-[0-9]+$'
GROUP BY company_id;

INSERT INTO document_sequences (company_id, document_type, `last_value`)
SELECT po.company_id, 'GOODS_RECEIPT', COALESCE(MAX(CAST(SUBSTRING(gr.receipt_number, 5) AS UNSIGNED)), 0)
FROM goods_receipts gr
JOIN purchase_orders po ON po.id = gr.purchase_order_id
WHERE gr.receipt_number REGEXP '^REC-[0-9]+$'
GROUP BY po.company_id;

-- Invoice ("SALE") folio is `series + "-" + number`; only `number` is the correlative part, and
-- historical numbers are System.currentTimeMillis()-derived (13+ digits) rather than sequential,
-- so there is nothing sane to backfill from — every company's SALE counter legitimately starts
-- at 0 here, same reasoning as the other timestamp/UUID-based types above.
