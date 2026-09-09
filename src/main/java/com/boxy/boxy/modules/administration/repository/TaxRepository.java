package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Tax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, Long> {
    List<Tax> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Optional<Tax> findByCompanyIdAndIsDefaultTrueAndDeletedAtIsNull(Long companyId);
    Optional<Tax> findByIdAndDeletedAtIsNull(Long id);
}
