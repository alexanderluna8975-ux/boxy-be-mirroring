package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    Page<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId, Pageable pageable);
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
    Page<StockMovement> findByWarehouseBranchCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    @Query("SELECT m FROM StockMovement m WHERE m.warehouse.branch.company.id = :companyId " +
           "AND (:warehouseId IS NULL OR m.warehouse.id = :warehouseId) " +
           "AND (:productId IS NULL OR m.product.id = :productId) " +
           "AND (:movementTypes IS NULL OR m.movementType IN :movementTypes) " +
           "AND (:dateFrom IS NULL OR m.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR m.createdAt <= :dateTo)")
    Page<StockMovement> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("movementTypes") Collection<String> movementTypes,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
