package com.boxy.boxy.modules.sales.repository;

import com.boxy.boxy.modules.sales.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
    List<Customer> findByCompanyIdAndDeletedAtIsNull(String companyId);
    Page<Customer> findByCompanyIdAndDeletedAtIsNull(String companyId, Pageable pageable);
    Optional<Customer> findByIdAndDeletedAtIsNull(String id);
    Optional<Customer> findByCompanyIdAndDocumentNumberAndDeletedAtIsNull(String companyId, String documentNumber);
}
