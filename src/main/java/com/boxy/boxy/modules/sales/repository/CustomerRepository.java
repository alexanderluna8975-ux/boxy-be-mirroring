package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Page<Customer> findByCompanyIdAndDeletedAtIsNull(Long companyId, Pageable pageable);
    Optional<Customer> findByIdAndDeletedAtIsNull(Long id);
    Optional<Customer> findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(Long companyId, String documentNumber);
}
