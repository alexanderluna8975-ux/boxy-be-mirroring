package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.BusinessException;
import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.CreateRoleRequest;
import com.boxy.boxy.modules.administration.dto.PermissionDto;
import com.boxy.boxy.modules.administration.dto.PermissionMatrixDto;
import com.boxy.boxy.modules.administration.dto.RoleDto;
import com.boxy.boxy.modules.administration.dto.RolePermissionsDto;
import com.boxy.boxy.modules.administration.dto.UpdateRoleRequest;
import com.boxy.boxy.modules.administration.entity.Company;
import com.boxy.boxy.modules.administration.entity.Permission;
import com.boxy.boxy.modules.administration.entity.Role;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.repository.CompanyRepository;
import com.boxy.boxy.modules.administration.repository.PermissionRepository;
import com.boxy.boxy.modules.administration.repository.RoleRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RoleDto> getAllRoles() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return roleRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDto getRoleById(Long id) {
        return toDto(findOwnedRole(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getRoleMembers(Long roleId) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        findOwnedRole(roleId); // 404s if the role doesn't belong to the caller's company
        return userRepository.findByRoleIdAndCompanyId(roleId, companyId).stream()
                .map(this::toMemberMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscActionAsc().stream()
                .map(this::toPermissionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PermissionMatrixDto getPermissionMatrix() {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        List<PermissionDto> catalog = permissionRepository.findAllByOrderByModuleAscActionAsc().stream()
                .map(this::toPermissionDto)
                .toList();
        List<RolePermissionsDto> roles = roleRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .sorted(Comparator.comparing(Role::getId))
                .map(this::toRolePermissionsDto)
                .toList();
        return PermissionMatrixDto.builder().catalog(catalog).roles(roles).build();
    }

    @Transactional
    public RolePermissionsDto updateRolePermissions(Long roleId, List<String> codes) {
        Role role = findOwnedRole(roleId);
        if ("ROLE_SUPER_ADMIN".equals(role.getCode())) {
            throw new BusinessException("IMMUTABLE_ROLE",
                    "The Super Administrator role has unrestricted access and cannot be modified.");
        }

        Set<String> requested = new LinkedHashSet<>(codes);
        List<Permission> found = permissionRepository.findByCodeIn(requested);
        if (found.size() != requested.size()) {
            Set<String> known = found.stream().map(Permission::getCode).collect(java.util.stream.Collectors.toSet());
            Set<String> unknown = new LinkedHashSet<>(requested);
            unknown.removeAll(known);
            throw new BusinessException("UNKNOWN_PERMISSION", "Unknown permission code(s): " + String.join(", ", unknown));
        }

        List<String> previousCodes = role.getPermissions().stream().map(Permission::getCode).sorted().toList();
        role.getPermissions().clear();
        role.getPermissions().addAll(found);
        Role saved = roleRepository.save(role);

        List<String> newCodes = found.stream().map(Permission::getCode).sorted().toList();
        auditLogService.record("Permisos actualizados", "Rol", String.valueOf(saved.getId()), saved.getName(),
                String.join(", ", previousCodes), String.join(", ", newCodes));
        return toRolePermissionsDto(saved);
    }

    @Transactional
    public RoleDto createRole(CreateRoleRequest request) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        String name = request.getName().trim();
        String code = generateUniqueRoleCode(companyId, name);

        Role role = Role.builder()
                .company(company)
                .code(code)
                .name(name)
                .description(request.getDescription())
                .isSystem(false)
                .build();

        Role saved = roleRepository.save(role);
        auditLogService.record("Rol creado", "Rol", String.valueOf(saved.getId()), saved.getName(), null, null);
        return toDto(saved);
    }

    @Transactional
    public RoleDto updateRole(Long id, UpdateRoleRequest request) {
        Role role = findOwnedRole(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException("IMMUTABLE_ROLE", "System roles cannot be renamed or edited.");
        }

        role.setName(request.getName().trim());
        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        Role saved = roleRepository.save(role);
        auditLogService.record("Rol actualizado", "Rol", String.valueOf(saved.getId()), saved.getName(), null, null);
        return toDto(saved);
    }

    @Transactional
    public void deleteRole(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        Role role = findOwnedRole(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException("IMMUTABLE_ROLE", "System roles cannot be deleted.");
        }

        long memberCount = userRepository.findByRoleIdAndCompanyId(id, companyId).size();
        if (memberCount > 0) {
            throw new BusinessException("ROLE_IN_USE",
                    "This role is assigned to " + memberCount + " user(s) and cannot be deleted.");
        }

        role.setDeletedAt(java.time.Instant.now());
        roleRepository.save(role);
        auditLogService.record("Rol eliminado", "Rol", String.valueOf(id), role.getName(), null, null);
    }

    /** Derives a stable `ROLE_*` code from the display name, appending a numeric suffix on collision —
     *  the form only collects a name; `code` exists for the same DB/lookup convenience as `Category.code`. */
    private String generateUniqueRoleCode(Long companyId, String name) {
        String base = "ROLE_" + name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.equals("ROLE_") || base.isBlank()) {
            base = "ROLE_CUSTOM";
        }
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        String candidate = base;
        int suffix = 2;
        while (roleRepository.findByCompanyIdAndCodeAndDeletedAtIsNull(companyId, candidate).isPresent()) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    /** 404s (not 403) on a role belonging to another company — same treatment as "doesn't exist". */
    private Role findOwnedRole(Long id) {
        Long companyId = SecurityUtils.requireCurrentCompanyId();
        return roleRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    private Map<String, String> toMemberMap(User u) {
        return Map.of(
                "fullName", u.getFullName() != null ? u.getFullName() : u.getUsername(),
                "email", u.getEmail() != null ? u.getEmail() : ""
        );
    }

    private RoleDto toDto(Role role) {
        List<PermissionDto> permissionDtos = role.getPermissions().stream()
                .map(this::toPermissionDto)
                .toList();

        long userCount = userRepository.findByRoleIdAndCompanyId(role.getId(), role.getCompany().getId()).size();

        return RoleDto.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .label(role.getName())
                .description(role.getDescription())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .userCount(userCount)
                .permissions(permissionDtos)
                .build();
    }

    private RolePermissionsDto toRolePermissionsDto(Role role) {
        List<String> codes = role.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
        return RolePermissionsDto.builder()
                .roleId(role.getId())
                .roleCode(role.getCode())
                .roleName(role.getName())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .permissions(codes)
                .build();
    }

    private PermissionDto toPermissionDto(Permission permission) {
        return PermissionDto.builder()
                .id(permission.getId())
                .module(permission.getModule())
                .action(permission.getAction())
                .code(permission.getCode())
                .description(permission.getDescription())
                .build();
    }
}
