package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, String> {
    List<UnitOfMeasure> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<UnitOfMeasure> findByIdAndDeletedAtIsNull(String id);
}
