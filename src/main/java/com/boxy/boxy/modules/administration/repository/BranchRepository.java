package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, String> {
    List<Branch> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<Branch> findByIdAndDeletedAtIsNull(String id);
    Optional<Branch> findByCompanyIdAndCodeAndDeletedAtIsNull(String companyId, String code);
}
