package com.boxy.boxy.modules.purchasing.repository;

import com.boxy.boxy.modules.purchasing.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Page<PurchaseOrder> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
    java.util.List<PurchaseOrder> findByCompanyId(Long companyId);

    long countBySupplierId(Long supplierId);

    @Query("SELECT MAX(po.createdAt) FROM PurchaseOrder po WHERE po.supplier.id = :supplierId")
    Instant findLastOrderDateBySupplierId(@Param("supplierId") Long supplierId);

    /** Excludes drafts/rejected/cancelled orders — those never represented real spend. */
    @Query("SELECT COALESCE(SUM(po.totalAmount), 0) FROM PurchaseOrder po " +
            "WHERE po.supplier.id = :supplierId AND UPPER(po.status) NOT IN ('DRAFT', 'REJECTED', 'CANCELLED')")
    BigDecimal sumTotalAmountBySupplierId(@Param("supplierId") Long supplierId);

    /** No `warehouseId` filter — the entity only tracks a `branch`, not a specific warehouse. */
    @Query("SELECT po FROM PurchaseOrder po WHERE po.company.id = :companyId " +
            "AND (:ignoreStatus = TRUE OR UPPER(po.status) IN :statuses) " +
            "AND (:supplierId IS NULL OR po.supplier.id = :supplierId) " +
            "AND (:branchId IS NULL OR po.branch.id = :branchId) " +
            "AND (:search IS NULL OR LOWER(po.orderNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(po.supplier.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:dateFrom IS NULL OR po.issueDate >= :dateFrom) " +
            "AND (:dateTo IS NULL OR po.issueDate <= :dateTo) " +
            "ORDER BY po.createdAt DESC")
    Page<PurchaseOrder> search(@Param("companyId") Long companyId,
                               @Param("ignoreStatus") boolean ignoreStatus,
                               @Param("statuses") List<String> statuses,
                               @Param("supplierId") Long supplierId,
                               @Param("branchId") Long branchId,
                               @Param("search") String search,
                               @Param("dateFrom") LocalDate dateFrom,
                               @Param("dateTo") LocalDate dateTo,
                               Pageable pageable);
}
