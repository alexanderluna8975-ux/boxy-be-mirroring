package com.boxy.boxy.modules.purchasing.repository;

import com.boxy.boxy.modules.purchasing.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Page<Supplier> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);
    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);
}
