package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Page<Invoice> findByBranchIdOrderByCreatedAtDesc(String branchId, Pageable pageable);
    Page<Invoice> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);
    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);
    Optional<Invoice> findByBranchIdAndDocumentTypeAndSeriesAndNumber(String branchId, String documentType, String series, String number);
}
