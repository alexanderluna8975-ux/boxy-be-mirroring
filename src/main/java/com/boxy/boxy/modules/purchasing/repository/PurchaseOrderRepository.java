package com.boxy.boxy.modules.purchasing.repository;

import com.boxy.boxy.modules.purchasing.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    Page<PurchaseOrder> findByBranchIdOrderByCreatedAtDesc(String branchId, Pageable pageable);
    Page<PurchaseOrder> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);
}
