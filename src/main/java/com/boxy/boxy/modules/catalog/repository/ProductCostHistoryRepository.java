package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.ProductCostHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductCostHistoryRepository extends JpaRepository<ProductCostHistory, Long> {
    Page<ProductCostHistory> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);
}
