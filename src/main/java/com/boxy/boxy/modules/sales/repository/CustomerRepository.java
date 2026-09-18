package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Page<Customer> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);
    Optional<Customer> findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(Long companyId, String documentNumber);

    @Query("SELECT c FROM Customer c WHERE c.company.id = :companyId AND c.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(c.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:isActive IS NULL OR c.isActive = :isActive)")
    Page<Customer> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
