package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    Optional<StockLevel> findByWarehouseIdAndProductIdAndVariantIdIsNull(Long warehouseId, Long productId);
    Optional<StockLevel> findByWarehouseIdAndProductIdAndVariantId(Long warehouseId, Long productId, Long variantId);
    List<StockLevel> findByWarehouseId(Long warehouseId);
    List<StockLevel> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.product.id = :productId AND s.variant.id IS NULL")
    Optional<StockLevel> findForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.product.id = :productId AND s.variant.id = :variantId")
    Optional<StockLevel> findVariantForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId, @Param("variantId") Long variantId);

    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.branch.id = :branchId AND s.quantityAvailable <= s.product.minStockAlert")
    List<StockLevel> findLowStockByBranch(@Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(s.quantityAvailable), 0) FROM StockLevel s WHERE s.product.id = :productId")
    java.math.BigDecimal getTotalAvailableStockByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(DISTINCT s.product.id) FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.quantityAvailable > 0")
    int countDistinctProductsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantityAvailable * p.costPrice), 0) FROM StockLevel s JOIN s.product p WHERE s.warehouse.id = :warehouseId")
    java.math.BigDecimal calculateStockValueByWarehouseId(@Param("warehouseId") Long warehouseId);
}

