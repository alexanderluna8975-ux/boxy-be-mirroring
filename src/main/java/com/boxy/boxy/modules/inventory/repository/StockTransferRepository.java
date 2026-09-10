package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.StockTransfer;
import com.boxy.boxy.modules.inventory.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    Page<StockTransfer> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    Optional<StockTransfer> findByTransferNumber(String transferNumber);
    Optional<StockTransfer> findByIdAndCompanyId(Long id, Long companyId);

    @Query("SELECT t FROM StockTransfer t WHERE t.company.id = :companyId " +
           "AND (:search IS NULL OR LOWER(t.transferNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:sourceWarehouseId IS NULL OR t.sourceWarehouse.id = :sourceWarehouseId) " +
           "AND (:destinationWarehouseId IS NULL OR t.destinationWarehouse.id = :destinationWarehouseId) " +
           "AND (:dateFrom IS NULL OR t.createdAt >= :dateFrom) " +
           "AND (:dateTo IS NULL OR t.createdAt <= :dateTo)")
    Page<StockTransfer> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("status") TransferStatus status,
            @Param("sourceWarehouseId") Long sourceWarehouseId,
            @Param("destinationWarehouseId") Long destinationWarehouseId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}
