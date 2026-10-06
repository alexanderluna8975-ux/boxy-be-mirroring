package com.boxy.boxy.modules.notifications.repository;

import com.boxy.boxy.modules.administration.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Who should hear about something, decided from roles rather than a per-user subscription list.
 * A user qualifies if they are active in the company and either
 * <ul>
 *   <li>hold {@code ROLE_SUPER_ADMIN} (anywhere — a super admin oversees every branch), or</li>
 *   <li>have a role <i>in the given branch</i> that grants one of the permission codes / is one of
 *   the role codes asked for.</li>
 * </ul>
 * Deliberately based on role permissions only: a per-user permission override (grant/revoke JSON on
 * the user) is not consulted. That can over- or under-include a user who has one, which is the
 * right trade for a notification — it only points at a screen whose own permission check still applies.
 */
public interface NotificationRecipientRepository extends Repository<User, Long> {

    @Query("""
            SELECT DISTINCT u.id FROM User u
            WHERE u.company.id = :companyId AND u.deletedAt IS NULL AND u.status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM UserBranchRole ubr JOIN ubr.role r LEFT JOIN r.permissions p
                WHERE ubr.user = u
                  AND (r.code = 'ROLE_SUPER_ADMIN'
                       OR (ubr.branch.id = :branchId AND p.code IN :permissionCodes)))
            """)
    List<Long> findUserIdsWithPermissionInBranch(@Param("companyId") Long companyId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("permissionCodes") Collection<String> permissionCodes);

    @Query("""
            SELECT DISTINCT u.id FROM User u
            WHERE u.company.id = :companyId AND u.deletedAt IS NULL AND u.status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM UserBranchRole ubr JOIN ubr.role r
                WHERE ubr.user = u
                  AND (r.code = 'ROLE_SUPER_ADMIN'
                       OR (ubr.branch.id = :branchId AND r.code IN :roleCodes)))
            """)
    List<Long> findUserIdsWithRoleInBranch(@Param("companyId") Long companyId,
                                           @Param("branchId") Long branchId,
                                           @Param("roleCodes") Collection<String> roleCodes);

    /** Company-wide (any branch) holders of one of the roles, plus super admins. */
    @Query("""
            SELECT DISTINCT u.id FROM User u
            WHERE u.company.id = :companyId AND u.deletedAt IS NULL AND u.status = 'ACTIVE'
              AND EXISTS (
                SELECT 1 FROM UserBranchRole ubr JOIN ubr.role r
                WHERE ubr.user = u AND (r.code = 'ROLE_SUPER_ADMIN' OR r.code IN :roleCodes))
            """)
    List<Long> findUserIdsWithRoleInCompany(@Param("companyId") Long companyId,
                                            @Param("roleCodes") Collection<String> roleCodes);
}
