package com.boxy.boxy.modules.sales.entity;

import com.boxy.boxy.modules.administration.entity.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_adjustments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, unique = true, length = 50)
    private String folio;

    @Column(name = "margin_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal marginPercent;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "applied_by", nullable = false, length = 100)
    private String appliedBy;

    @CreationTimestamp
    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "priceAdjustment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PriceAdjustmentLine> lines = new ArrayList<>();
}
