package com.boxy.boxy.modules.catalog.repository;

import com.boxy.boxy.modules.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Optional<Category> findByIdAndDeletedAtIsNull(String id);
}
