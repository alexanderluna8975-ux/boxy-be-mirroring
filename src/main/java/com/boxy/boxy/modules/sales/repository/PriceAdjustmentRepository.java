package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.PriceAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PriceAdjustmentRepository extends JpaRepository<PriceAdjustment, Long> {

    Page<PriceAdjustment> findByCompanyIdOrderByAppliedAtDesc(Long companyId, Pageable pageable);

    Optional<PriceAdjustment> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyId(Long companyId);

    @Query("SELECT pa FROM PriceAdjustment pa WHERE pa.company.id = :companyId " +
           "AND (:search IS NULL OR LOWER(pa.folio) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:from IS NULL OR pa.appliedAt >= :from) " +
           "AND (:to IS NULL OR pa.appliedAt <= :to) " +
           "ORDER BY pa.appliedAt DESC")
    Page<PriceAdjustment> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
