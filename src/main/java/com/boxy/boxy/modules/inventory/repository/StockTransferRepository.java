package com.boxy.boxy.modules.inventory.repository;

import com.boxy.boxy.modules.inventory.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    Page<StockTransfer> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    Optional<StockTransfer> findByTransferNumber(String transferNumber);
}
