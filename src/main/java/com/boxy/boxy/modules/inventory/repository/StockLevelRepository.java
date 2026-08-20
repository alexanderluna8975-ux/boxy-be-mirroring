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
public interface StockLevelRepository extends JpaRepository<StockLevel, String> {
    Optional<StockLevel> findByWarehouseIdAndProductIdAndVariantIdIsNull(String warehouseId, String productId);
    Optional<StockLevel> findByWarehouseIdAndProductIdAndVariantId(String warehouseId, String productId, String variantId);
    List<StockLevel> findByWarehouseId(String warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.product.id = :productId AND s.variant.id IS NULL")
    Optional<StockLevel> findForUpdate(@Param("warehouseId") String warehouseId, @Param("productId") String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.product.id = :productId AND s.variant.id = :variantId")
    Optional<StockLevel> findVariantForUpdate(@Param("warehouseId") String warehouseId, @Param("productId") String productId, @Param("variantId") String variantId);

    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.branch.id = :branchId AND s.quantityAvailable <= s.product.minStockAlert")
    List<StockLevel> findLowStockByBranch(@Param("branchId") String branchId);
}
