package com.boxy.boxy.modules.purchasing.repository;

import com.boxy.boxy.modules.purchasing.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    Page<GoodsReceipt> findByWarehouseBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
    Optional<GoodsReceipt> findByReceiptNumber(String receiptNumber);
}
