package com.boxy.boxy.core.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One row of a money-shaped printable table — shared by Cotización, Nota de Venta, Orden de
 * Compra and Recepción (their tables all boil down to cantidad/código/descripción/precio-o-costo/
 * subtotal, just with different column labels). PDF-only: never serialized over the REST API, so
 * it's free to be one shape reused across templates instead of a DTO per document type.
 * <p>
 * The Nota de Transferencia table has no money in it at all, so it renders straight off
 * {@code StockTransferItem} instead of this class.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfLineItem {
    private String sku;
    private String description;
    private String unitName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal subtotal;
}
