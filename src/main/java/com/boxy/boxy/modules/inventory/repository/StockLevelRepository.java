package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.administration.entity.Warehouse;
import com.boxy.boxy.modules.catalog.entity.Product;
import com.boxy.boxy.modules.inventory.entity.StockLevel;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    Optional<StockLevel> findByWarehouseIdAndProductIdAndVariantIdIsNull(Long warehouseId, Long productId);
    List<StockLevel> findByWarehouseId(Long warehouseId);
    List<StockLevel> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.product.id = :productId AND s.variant.id IS NULL")
    Optional<StockLevel> findForUpdate(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Query("SELECT s FROM StockLevel s WHERE s.warehouse.branch.id = :branchId AND s.quantityAvailable <= s.product.minStockAlert")
    List<StockLevel> findLowStockByBranch(@Param("branchId") Long branchId);

    @Query("SELECT COALESCE(SUM(s.quantityAvailable), 0) FROM StockLevel s WHERE s.product.id = :productId")
    BigDecimal getTotalAvailableStockByProductId(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(s.quantityInTransit), 0) FROM StockLevel s WHERE s.product.id = :productId")
    BigDecimal getTotalInTransitStockByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(DISTINCT s.product.id) FROM StockLevel s WHERE s.warehouse.id = :warehouseId AND s.quantityAvailable > 0")
    int countDistinctProductsByWarehouseId(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(s.quantityAvailable * p.costPrice), 0) FROM StockLevel s JOIN s.product p WHERE s.warehouse.id = :warehouseId")
    BigDecimal calculateStockValueByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * Locks the existing row for {@code warehouse}+{@code product}, or creates it at zero
     * and locks that instead. {@code findForUpdate(...).orElseGet(() -> new StockLevel(...))}
     * looks equivalent but isn't: {@code SELECT ... FOR UPDATE} cannot lock a row that doesn't
     * exist yet, so two concurrent callers can both take the "create" branch and collide on
     * {@code uk_stock_location}. This retries as a same-row read on that collision instead of
     * surfacing a raw {@link DataIntegrityViolationException}.
     */
    default StockLevel getOrCreateForUpdate(Warehouse warehouse, Product product) {
        return findForUpdate(warehouse.getId(), product.getId())
                .orElseGet(() -> {
                    try {
                        return saveAndFlush(StockLevel.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantityAvailable(BigDecimal.ZERO)
                                .quantityReserved(BigDecimal.ZERO)
                                .quantityInTransit(BigDecimal.ZERO)
                                .build());
                    } catch (DataIntegrityViolationException raceLostToAnotherInsert) {
                        return findForUpdate(warehouse.getId(), product.getId())
                                .orElseThrow(() -> raceLostToAnotherInsert);
                    }
                });
    }
}
