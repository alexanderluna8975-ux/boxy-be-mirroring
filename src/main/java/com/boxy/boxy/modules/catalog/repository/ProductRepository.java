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
           "(:categoryId IS NULL OR p.category.id = :categoryId) AND " +
           "(:brandId IS NULL OR p.brand.id = :brandId) AND " +
           "(:isActive IS NULL OR p.isActive = :isActive)")
    Page<Product> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("brandId") Long brandId,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    List<Product> findTop10ByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId);
    List<Product> findByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(Long companyId);
    long countByCompanyIdAndIsActiveTrueAndDeletedAtIsNull(Long companyId);

    long countByCategoryIdAndDeletedAtIsNull(Long categoryId);
    long countByBrandIdAndDeletedAtIsNull(Long brandId);
    long countByUnitIdAndDeletedAtIsNull(Long unitId);
}
