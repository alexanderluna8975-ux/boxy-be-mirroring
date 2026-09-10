package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.PriceAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceAdjustmentRepository extends JpaRepository<PriceAdjustment, Long> {

    Page<PriceAdjustment> findByCompanyIdOrderByAppliedAtDesc(Long companyId, Pageable pageable);

    Optional<PriceAdjustment> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyId(Long companyId);
}
