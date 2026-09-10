package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.CreateUserRequest;
import com.boxy.boxy.modules.administration.dto.UpdateUserRequest;
import com.boxy.boxy.modules.administration.dto.UserBranchAssignmentDto;
import com.boxy.boxy.modules.administration.dto.UserBranchAssignmentRequest;
import com.boxy.boxy.modules.administration.dto.UserDto;
import com.boxy.boxy.modules.administration.entity.Branch;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Role;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import com.boxy.boxy.modules.administration.repository.BranchRepository;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.RoleRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return userRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<UserDto> getUsers(String search, String status, Long roleId, Pageable pageable) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return userRepository.findAllFiltered(companyId, blankToNull(search), blankToNull(status), roleId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return toDto(findOwnedUser(id));
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            username = request.getEmail().split("@")[0];
        }

        String firstName = request.getFirstName();
        String lastName = request.getLastName();
        if (firstName == null || firstName.isBlank()) {
            firstName = username;
        }
        if (lastName == null || lastName.isBlank()) {
            lastName = "";
        }

        if (userRepository.findByUsernameAndDeletedAtIsNull(username).isPresent()) {
            throw new BusinessException("USERNAME_EXISTS", "Username already in use.");
        }
        if (userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).isPresent()) {
            throw new BusinessException("EMAIL_EXISTS", "Email already in use.");
        }

        User user = User.builder()
                .company(company)
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(firstName)
                .lastName(lastName)
                .avatarUrl(request.getAvatarUrl())
                .status("ACTIVE")
                .build();

        applyBranchAssignments(user, request.getBranchAssignments(), companyId);

        User saved = userRepository.save(user);
        auditLogService.record("Usuario creado", "Usuario", String.valueOf(saved.getId()),
                saved.getFullName() != null ? saved.getFullName() : saved.getUsername(),
                null, "correo: " + saved.getEmail());
        return toDto(saved);
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        User user = findOwnedUser(id);
        String previous = describeUser(user);

        if (!user.getUsername().equalsIgnoreCase(request.getUsername())) {
            userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername()).ifPresent(existing -> {
                throw new BusinessException("USERNAME_EXISTS", "Username already in use.");
            });
            user.setUsername(request.getUsername());
        }
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).ifPresent(existing -> {
                throw new BusinessException("EMAIL_EXISTS", "Email already in use.");
            });
            user.setEmail(request.getEmail());
        }
        if (request.getFirstName() != null && !request.getFirstName().isBlank()) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        if (request.getBranchAssignments() != null && !request.getBranchAssignments().isEmpty()) {
            user.getBranchRoles().clear();
            // Flush the orphan-removal DELETEs before inserting the new rows: without this,
            // re-assigning the exact same (branch, role) pair the user already had collides
            // with uk_user_branch, because Hibernate would otherwise try the INSERT before
            // the DELETE of the cleared collection is applied.
            userRepository.saveAndFlush(user);
            applyBranchAssignments(user, request.getBranchAssignments(), companyId);
        }

        User saved = userRepository.save(user);
        auditLogService.record("Usuario actualizado", "Usuario", String.valueOf(saved.getId()),
                saved.getFullName(), previous, describeUser(saved));
        return toDto(saved);
    }

    /** Admin-initiated reset — deliberately a separate action from {@link #updateUser}, which
     * can no longer touch the password at all (see plan §1.1: any authenticated caller could
     * previously hijack another account's password as a side effect of an unrelated edit). */
    @Transactional
    public UserDto resetPassword(Long id, String newPassword) {
        User user = findOwnedUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        User saved = userRepository.save(user);
        // Never logs the password itself, before or after — only that a reset happened.
        auditLogService.record("Contraseña restablecida", "Usuario", String.valueOf(saved.getId()),
                saved.getFullName(), null, null);
        return toDto(saved);
    }

    @Transactional
    public UserDto deactivateUser(Long id) {
        User user = findOwnedUser(id);
        String previousStatus = user.getStatus();
        String newStatus = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "INACTIVE" : "ACTIVE";
        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        auditLogService.record("ACTIVE".equalsIgnoreCase(newStatus) ? "Usuario activado" : "Usuario desactivado",
                "Usuario", String.valueOf(saved.getId()), saved.getFullName(), previousStatus, newStatus);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public boolean checkUserUnique(String field, String value, Long excludeId) {
        Optional<User> existing = "email".equalsIgnoreCase(field)
                ? userRepository.findByEmailAndDeletedAtIsNull(value)
                : userRepository.findByUsernameAndDeletedAtIsNull(value);
        if (existing.isEmpty()) {
            return true;
        }
        return excludeId != null && existing.get().getId().equals(excludeId);
    }

    /** 404s (not 403) on a user belonging to another company — same treatment as "doesn't exist". */
    private User findOwnedUser(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return userRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private void applyBranchAssignments(User user, List<UserBranchAssignmentRequest> assignments, Long companyId) {
        if (assignments == null) {
            return;
        }
        for (UserBranchAssignmentRequest assignReq : assignments) {
            Branch branch = branchRepository.findByIdAndCompanyIdAndDeletedAtIsNull(assignReq.getBranchId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch", assignReq.getBranchId()));
            Role role = roleRepository.findByIdAndCompanyIdAndDeletedAtIsNull(assignReq.getRoleId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", assignReq.getRoleId()));

            UserBranchRole ubr = UserBranchRole.builder()
                    .user(user)
                    .branch(branch)
                    .role(role)
                    .isDefault(assignReq.isDefault())
                    .build();

            user.getBranchRoles().add(ubr);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String describeUser(User user) {
        return "usuario: " + user.getUsername() + "\ncorreo: " + user.getEmail();
    }

    private UserDto toDto(User user) {
        List<UserBranchAssignmentDto> assignments = user.getBranchRoles().stream()
                .map(ubr -> UserBranchAssignmentDto.builder()
                        .branchId(ubr.getBranch().getId())
                        .branchName(ubr.getBranch().getName())
                        .roleId(ubr.getRole().getId())
                        .roleName(ubr.getRole().getName())
                        .isDefault(Boolean.TRUE.equals(ubr.getIsDefault()))
                        .build())
                .toList();

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .branchAssignments(assignments)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
