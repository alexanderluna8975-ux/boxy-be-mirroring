package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBranchRoleRepository extends JpaRepository<UserBranchRole, Long> {
    List<UserBranchRole> findByUserId(Long userId);
    List<UserBranchRole> findByBranchId(Long branchId);
}
