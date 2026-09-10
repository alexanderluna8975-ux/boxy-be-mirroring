package com.boxy.boxy.modules.administration.repository;

import com.boxy.boxy.modules.administration.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    List<User> findByCompanyIdAndDeletedAtIsNull(Long companyId);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    Optional<User> findByIdAndCompanyIdAndDeletedAtIsNull(Long id, Long companyId);

    @Query("SELECT u FROM User u WHERE (u.username = :identifier OR u.email = :identifier) AND u.deletedAt IS NULL")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query("SELECT DISTINCT u FROM User u LEFT JOIN u.branchRoles ubr LEFT JOIN ubr.role r " +
           "WHERE u.company.id = :companyId AND u.deletedAt IS NULL " +
           "AND (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:roleId IS NULL OR r.id = :roleId)")
    Page<User> findAllFiltered(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("roleId") Long roleId,
            Pageable pageable);

    /** Used by {@code RoleService.getRoleMembers} — a role's members, scoped to the caller's company. */
    @Query("SELECT DISTINCT u FROM User u JOIN u.branchRoles ubr " +
           "WHERE ubr.role.id = :roleId AND u.company.id = :companyId AND u.deletedAt IS NULL")
    List<User> findByRoleIdAndCompanyId(@Param("roleId") Long roleId, @Param("companyId") Long companyId);
}
