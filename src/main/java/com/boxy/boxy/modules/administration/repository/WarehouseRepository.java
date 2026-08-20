package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, String> {
    List<Warehouse> findByBranchIdAndDeletedAtIsNull(String branchId);
    List<Warehouse> findByBranchCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<Warehouse> findByIdAndDeletedAtIsNull(String id);
    Optional<Warehouse> findByBranchIdAndIsDefaultTrueAndDeletedAtIsNull(String branchId);
}
