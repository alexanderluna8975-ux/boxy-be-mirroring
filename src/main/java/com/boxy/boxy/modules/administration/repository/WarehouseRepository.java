package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findByBranchIdAndDeletedAtIsNull(Long branchId);
    List<Warehouse> findByBranchCompanyIdAndDeletedAtIsNull(Long companyId);
    Optional<Warehouse> findByIdAndDeletedAtIsNull(Long id);
    Optional<Warehouse> findByBranchIdAndIsDefaultTrueAndDeletedAtIsNull(Long branchId);
    Optional<Warehouse> findByBranchCompanyIdAndNameIgnoreCaseAndDeletedAtIsNull(Long companyId, String name);
    Optional<Warehouse> findByBranchCompanyIdAndCodeIgnoreCaseAndDeletedAtIsNull(Long companyId, String code);
}
