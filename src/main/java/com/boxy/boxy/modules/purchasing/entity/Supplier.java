package com.boxy.boxy.modules.purchasing.entity;

import com.boxy.boxy.modules.administration.entity.Company;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Optional since the supplier form dropped the RFC field (V9); the unique
    // key (company_id, tax_id) still holds — MariaDB allows multiple NULLs.
    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "payment_terms_days", nullable = false)
    @Builder.Default
    private Integer paymentTermsDays = 0;

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
