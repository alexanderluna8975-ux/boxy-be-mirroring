package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.AdjustmentStatus;
import com.boxy.boxy.modules.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    Page<StockAdjustment> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    Optional<StockAdjustment> findByAdjustmentNumber(String adjustmentNumber);
    Optional<StockAdjustment> findByIdAndCompanyId(Long id, Long companyId);

    @Query("SELECT a FROM StockAdjustment a WHERE a.company.id = :companyId " +
           "AND (:search IS NULL OR LOWER(a.adjustmentNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:warehouseId IS NULL OR a.warehouse.id = :warehouseId) " +
           "AND (:reasonName IS NULL OR a.reason = :reasonName) " +
           "AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR a.createdAt <= :dateTo)")
    Page<StockAdjustment> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("status") AdjustmentStatus status,
            @Param("warehouseId") Long warehouseId,
            @Param("reasonName") String reasonName,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
