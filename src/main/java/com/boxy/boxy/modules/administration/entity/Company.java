package com.boxy.boxy.modules.administration.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "companies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "tax_id", nullable = false, length = 50)
    private String taxId;

    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "currency_code", nullable = false, length = 10)
    @Builder.Default
    private String currencyCode = "USD";

    @Column(name = "currency_symbol", nullable = false, length = 5)
    @Builder.Default
    private String currencySymbol = "$";

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "allow_negative_stock", nullable = false)
    @Builder.Default
    private Boolean allowNegativeStock = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
