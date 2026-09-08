package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);
}
