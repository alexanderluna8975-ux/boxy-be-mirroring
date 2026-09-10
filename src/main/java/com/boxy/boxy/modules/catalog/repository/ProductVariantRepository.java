package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductIdAndDeletedAtIsNull(Long productId);
    Optional<ProductVariant> findBySkuAndDeletedAtIsNull(String sku);
}
