package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, String> {
}
