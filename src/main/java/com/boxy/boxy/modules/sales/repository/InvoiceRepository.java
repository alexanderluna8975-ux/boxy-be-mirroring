package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Page<Invoice> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
    Page<Invoice> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    java.util.List<Invoice> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);
    Optional<Invoice> findByBranchIdAndDocumentTypeAndSeriesAndNumber(Long branchId, String documentType, String series, String number);
    Optional<Invoice> findFirstBySalesOrderId(Long salesOrderId);
}
