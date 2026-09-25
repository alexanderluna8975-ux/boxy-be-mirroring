package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);
    Optional<Product> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);
    Optional<Product> findByCompanyIdAndSkuAndDeletedAtIsNull(Long companyId, String sku);
    Optional<Product> findByCompanyIdAndSkuIgnoreCaseAndDeletedAtIsNull(Long companyId, String sku);
    Optional<Product> findByCompanyIdAndBarcodeAndDeletedAtIsNull(Long companyId, String barcode);

    @Query("SELECT p FROM Product p WHERE p.company.id = :companyId AND p.deletedAt IS NULL AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) OR p.barcode LIKE CONCAT('%', :search, '%')) AND " +
           "(:categoryIds IS NULL OR p.category.id IN :categoryIds) AND " +
           "(:brandIds IS NULL OR p.brand.id IN :brandIds) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive) AND " +
           "(:warehouseIds IS NULL OR EXISTS (" +
           "  SELECT 1 FROM StockLevel wsl WHERE wsl.product = p AND wsl.warehouse.id IN :warehouseIds AND wsl.quantityAvailable > 0" +
           ")) AND " +
           "(:stockStatusFilterActive = false OR (" +
           "  (:matchInStock = true AND (SELECT COALESCE(SUM(s1.quantityAvailable), 0) FROM StockLevel s1 WHERE s1.product = p) > p.minStockAlert) OR " +
           "  (:matchLowStock = true AND (SELECT COALESCE(SUM(s2.quantityAvailable), 0) FROM StockLevel s2 WHERE s2.product = p) > 0 AND (SELECT COALESCE(SUM(s2.quantityAvailable), 0) FROM StockLevel s2 WHERE s2.product = p) <= p.minStockAlert) OR " +
           "  (:matchOutOfStock = true AND (SELECT COALESCE(SUM(s3.quantityAvailable), 0) FROM StockLevel s3 WHERE s3.product = p) <= 0) OR " +
           "  (:matchInTransit = true AND (SELECT COALESCE(SUM(s4.quantityInTransit), 0) FROM StockLevel s4 WHERE s4.product = p) > 0)" +
           "))")
    Page<Product> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("brandIds") List<Long> brandIds,
            @Param("isActive") Boolean isActive,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("stockStatusFilterActive") boolean stockStatusFilterActive,
            @Param("matchInStock") boolean matchInStock,
            @Param("matchLowStock") boolean matchLowStock,
            @Param("matchOutOfStock") boolean matchOutOfStock,
            @Param("matchInTransit") boolean matchInTransit,
            Pageable pageable);

    List<Product> findTop10ByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId);
    List<Product> findByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(Long companyId);
    long countByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(Long companyId);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);
    long countByBrandIdAndDeletedAtIsNull(Long brandId);
    long countByUnitIdAndDeletedAtIsNull(Long unitId);
}
