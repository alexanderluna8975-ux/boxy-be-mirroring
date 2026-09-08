package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Optional<Role> findByCompanyIdAndCodeAndDeletedAtIsNull(Long companyId, String code);
    Optional<Role> findByIdAndDeletedAtIsNull(Long id);
}