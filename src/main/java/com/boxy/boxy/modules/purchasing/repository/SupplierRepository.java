package com.boxy.boxy.modules.purchasing.repository;

import com.boxy.boxy.modules.purchasing.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Page<Supplier> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);
    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);
    Optional<Supplier> findByCompanyIdAndTaxIdAndDeletedAtIsNull(Long companyId, String taxId);

    /** No `city` filter — the entity has no such column (only a free-text `address`). */
    @Query("SELECT s FROM Supplier s WHERE s.company.id = :companyId AND s.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(s.taxId) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(s.contactName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:isActive IS NULL OR s.isActive = :isActive)")
    Page<Supplier> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
