package com.boxy.boxy.modules.purchasing.dto;

import lombok.Data;

import java.math.BigDecimal;

/** Inline edit of a single line — quantity, cost, and the product's sale price — allowed only
 *  while the order is pending approval. See {@code PurchasingService#updatePurchaseOrderLine}. */
@Data
public class UpdatePurchaseOrderLineRequest {
    private BigDecimal quantity;
    private BigDecimal unitCost;
    /** Optional — omit to leave the product's sale price untouched. */
    private BigDecimal salePrice;
}
