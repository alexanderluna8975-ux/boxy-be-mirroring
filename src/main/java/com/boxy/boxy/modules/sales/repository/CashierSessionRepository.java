package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.CashierSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashierSessionRepository extends JpaRepository<CashierSession, Long> {
    Optional<CashierSession> findByUserIdAndBranchIdAndStatus(Long userId, Long branchId, String status);
    List<CashierSession> findByBranchIdOrderByOpenedAtDesc(Long branchId);
    Optional<CashierSession> findByIdAndBranchCompanyId(Long id, Long companyId);

    /**
     * Cash registers of a company, newest first (sort comes from the pageable). {@code userId} narrows
     * to one cashier's own registers — what a non-admin sees.
     */
    @Query("SELECT s FROM CashierSession s WHERE s.branch.company.id = :companyId " +
           "AND (:userId IS NULL OR s.user.id = :userId) " +
           "AND (:branchId IS NULL OR s.branch.id = :branchId) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<CashierSession> search(@Param("companyId") Long companyId,
                                @Param("userId") Long userId,
                                @Param("branchId") Long branchId,
                                @Param("status") String status,
                                Pageable pageable);
}
