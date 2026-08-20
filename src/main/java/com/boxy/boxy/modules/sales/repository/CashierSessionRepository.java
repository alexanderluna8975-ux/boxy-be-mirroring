package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.CashierSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashierSessionRepository extends JpaRepository<CashierSession, String> {
    Optional<CashierSession> findByUserIdAndBranchIdAndStatus(String userId, String branchId, String status);
    List<CashierSession> findByBranchIdOrderByOpenedAtDesc(String branchId);
}
