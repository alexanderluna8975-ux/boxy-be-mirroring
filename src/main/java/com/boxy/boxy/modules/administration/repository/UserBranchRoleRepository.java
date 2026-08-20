package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBranchRoleRepository extends JpaRepository<UserBranchRole, String> {
    List<UserBranchRole> findByUserId(String userId);
    List<UserBranchRole> findByBranchId(String branchId);
}
