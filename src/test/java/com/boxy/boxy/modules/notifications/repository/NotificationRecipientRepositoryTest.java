package com.boxy.boxy.modules.notifications.repository;

import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Permission;
import com.boxy.boxy.modules.administration.entity.Role;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who a notification is addressed to is a data-visibility decision: too wide and one branch hears
 * about another's stock, transfers or voided sales. So the recipient queries are checked against the
 * real schema with real rows (rolled back afterwards) rather than trusted by reading the JPQL.
 * Uses the same local MySQL {@code BoxyApplicationTests} needs; everything it inserts is unique to
 * the test, so it neither depends on nor disturbs seed data.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-only-jwt-signing-secret-do-not-use-in-any-real-environment-1234567890",
        "app.recaptcha.enabled=false"
})
@Transactional
class NotificationRecipientRepositoryTest {

    @Autowired private NotificationRecipientRepository repository;
    @PersistenceContext private EntityManager em;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String permission;

    private Company companyA;
    private Company companyB;
    private Branch branch1;
    private Branch branch2;
    private Role inventoryRole;
    private Role emptyRole;
    private Role managerRole;
    private Role superAdminRole;

    @BeforeEach
    void seed() {
        permission = "test:notif-" + tag;
        Permission perm = persist(Permission.builder().module("test").action("notif-" + tag).code(permission).build());

        companyA = company("A");
        companyB = company("B");
        branch1 = branch(companyA, "1");
        branch2 = branch(companyA, "2");

        inventoryRole = role(companyA, "ROLE_INV_" + tag, Set.of(perm));
        emptyRole = role(companyA, "ROLE_EMPTY_" + tag, Set.of());
        managerRole = role(companyA, "ROLE_MANAGER", Set.of());
        superAdminRole = role(companyA, "ROLE_SUPER_ADMIN", Set.of());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private Company company(String suffix) {
        return persist(Company.builder().name("Test Co " + suffix + " " + tag).taxId("TAX-" + suffix + tag).build());
    }

    private Branch branch(Company company, String suffix) {
        return persist(Branch.builder().company(company).code("B" + suffix + tag).name("Branch " + suffix).build());
    }

    private Role role(Company company, String code, Set<Permission> permissions) {
        return persist(Role.builder().company(company).code(code).name(code).permissions(new HashSet<>(permissions)).build());
    }

    private User user(Company company, String name, Branch branch, Role role, String status, Instant deletedAt) {
        User user = persist(User.builder().company(company).username(name + tag).email(name + tag + "@t.dev")
                .passwordHash("x").firstName(name).lastName("Test").status(status).deletedAt(deletedAt).build());
        persist(UserBranchRole.builder().user(user).branch(branch).role(role).isDefault(true).build());
        return user;
    }

    private User active(Company company, String name, Branch branch, Role role) {
        return user(company, name, branch, role, "ACTIVE", null);
    }

    private List<Long> byPermission(Company company, Branch branch) {
        em.flush();
        return repository.findUserIdsWithPermissionInBranch(company.getId(), branch.getId(), Set.of(permission));
    }

    // ---- by permission ----

    @Test
    void returnsUsersWhoseRoleInThatBranchGrantsThePermission() {
        User inBranch1 = active(companyA, "u1", branch1, inventoryRole);

        assertThat(byPermission(companyA, branch1)).containsExactly(inBranch1.getId());
    }

    @Test
    void aUserOfAnotherBranchOfTheSameCompanyIsNotIncluded() {
        active(companyA, "u1", branch1, inventoryRole);
        User inBranch2 = active(companyA, "u2", branch2, inventoryRole);

        assertThat(byPermission(companyA, branch2)).containsExactly(inBranch2.getId());
    }

    @Test
    void aRoleWithoutThePermissionIsNotIncluded() {
        active(companyA, "u3", branch1, emptyRole);

        assertThat(byPermission(companyA, branch1)).isEmpty();
    }

    @Test
    void aSuperAdminAssignedElsewhereIsStillIncluded() {
        User superAdmin = active(companyA, "root", branch2, superAdminRole);

        assertThat(byPermission(companyA, branch1)).containsExactly(superAdmin.getId());
    }

    @Test
    void inactiveAndDeletedUsersAreExcluded() {
        user(companyA, "off", branch1, inventoryRole, "INACTIVE", null);
        user(companyA, "gone", branch1, inventoryRole, "ACTIVE", Instant.now());
        User live = active(companyA, "live", branch1, inventoryRole);

        assertThat(byPermission(companyA, branch1)).containsExactly(live.getId());
    }

    @Test
    void neverCrossesCompanies() {
        Branch otherBranch = branch(companyB, "9");
        Role otherRole = role(companyB, "ROLE_INV_B_" + tag, Set.of(em.createQuery(
                "SELECT p FROM Permission p WHERE p.code = :c", Permission.class).setParameter("c", permission).getSingleResult()));
        active(companyB, "other", otherBranch, otherRole);

        assertThat(byPermission(companyA, branch1)).isEmpty();
        assertThat(byPermission(companyB, otherBranch)).hasSize(1);
    }

    @Test
    void aRoleMatchingSeveralRequestedPermissionsStillReturnsTheUserOnce() {
        // The permissions join produces one row per matching permission; DISTINCT must fold them.
        Permission second = persist(Permission.builder().module("test").action("notif2-" + tag).code(permission + "-2").build());
        Permission first = em.createQuery("SELECT p FROM Permission p WHERE p.code = :c", Permission.class)
                .setParameter("c", permission).getSingleResult();
        Role both = role(companyA, "ROLE_BOTH_" + tag, Set.of(first, second));
        User user = active(companyA, "multi", branch1, both);
        em.flush();

        List<Long> result = repository.findUserIdsWithPermissionInBranch(
                companyA.getId(), branch1.getId(), Set.of(permission, permission + "-2"));

        assertThat(result).containsExactly(user.getId());
    }

    // ---- by role ----

    @Test
    void roleLookupMatchesTheRoleInThatBranchAndSuperAdminsAnywhere() {
        User manager = active(companyA, "mgr", branch1, managerRole);
        active(companyA, "mgr2", branch2, managerRole);
        User superAdmin = active(companyA, "root", branch2, superAdminRole);
        active(companyA, "clerk", branch1, emptyRole);
        em.flush();

        List<Long> result = repository.findUserIdsWithRoleInBranch(companyA.getId(), branch1.getId(), Set.of("ROLE_MANAGER"));

        assertThat(result).containsExactlyInAnyOrder(manager.getId(), superAdmin.getId());
    }
}
