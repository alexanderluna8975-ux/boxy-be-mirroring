package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Tax;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRepository extends JpaRepository<Tax, String> {
    List<Tax> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<Tax> findByCompanyIdAndIsDefaultTrueAndDeletedAtIsNull(String companyId);
}
