package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, String> {
    Page<StockAdjustment> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);
    Optional<StockAdjustment> findByAdjustmentNumber(String adjustmentNumber);
}
