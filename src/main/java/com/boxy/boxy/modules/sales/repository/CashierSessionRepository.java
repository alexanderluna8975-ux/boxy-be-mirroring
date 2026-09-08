package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.CashierSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashierSessionRepository extends JpaRepository<CashierSession, Long> {
    Optional<CashierSession> findByUserIdAndBranchIdAndStatus(Long userId, Long branchId, String status);
    List<CashierSession> findByBranchIdOrderByOpenedAtDesc(Long branchId);
}
