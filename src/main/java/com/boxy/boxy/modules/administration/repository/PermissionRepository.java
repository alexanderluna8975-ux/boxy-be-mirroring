package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findAllByOrderByModuleAscActionAsc();
}
