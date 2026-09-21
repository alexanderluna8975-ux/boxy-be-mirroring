package com.boxy.boxy.modules.catalog.entity;

import com.boxy.boxy.modules.administration.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per product per goods receipt — records the weighted-average cost recalculation that
 * receiving stock triggered. {@code goodsReceiptId}/{@code goodsReceiptNumber} are a plain
 * cross-module reference (a DB foreign key still enforces it, see V15 migration) rather than a
 * JPA {@code @ManyToOne} to {@code purchasing.entity.GoodsReceipt} — same convention
 * {@link com.boxy.boxy.modules.inventory.entity.StockMovement}'s {@code referenceId} already
 * uses, so catalog doesn't have to depend on the purchasing module.
 */
@Entity
@Table(name = "product_cost_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCostHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** {@code Product.costPrice} immediately before this receipt was applied. */
    @Column(name = "previous_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal previousCost;

    /** {@code Product.costPrice} immediately after — the new weighted-average cost. */
    @Column(name = "new_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal newCost;

    /** What was actually paid per unit in THIS receipt — may differ from {@code newCost} (the average). */
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "quantity_received", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantityReceived;

    @Column(name = "goods_receipt_id", nullable = false)
    private Long goodsReceiptId;

    @Column(name = "goods_receipt_number", nullable = false, length = 50)
    private String goodsReceiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
