package com.boxy.boxy.modules.inventory.entity;

import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.catalog.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_levels", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_location", columnNames = {"warehouse_id", "product_id", "variant_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "quantity_available", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal quantityAvailable = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "quantity_in_transit", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal quantityInTransit = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
