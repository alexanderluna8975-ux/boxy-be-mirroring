package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);
}
