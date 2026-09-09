package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Page<SalesOrder> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);
    Optional<SalesOrder> findByOrderNumber(String orderNumber);
    Page<SalesOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SalesOrder> findByOrderTypeOrderByCreatedAtDesc(String orderType, Pageable pageable);
}
