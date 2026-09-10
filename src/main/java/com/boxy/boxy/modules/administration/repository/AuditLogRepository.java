package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.companyId = :companyId " +
            "AND (:entity IS NULL OR a.resourceType = :entity) " +
            "AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom) " +
            "AND (:dateTo IS NULL OR a.createdAt <= :dateTo) " +
            "AND (:search IS NULL " +
            "     OR LOWER(a.action) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(a.resourceType) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(a.resourceId) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("companyId") Long companyId,
                           @Param("search") String search,
                           @Param("entity") String entity,
                           @Param("dateFrom") Instant dateFrom,
                           @Param("dateTo") Instant dateTo,
                           Pageable pageable);
}
