package com.boxy.boxy.modules.administration.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.security.SecurityUtils;
import com.boxy.boxy.modules.administration.dto.PermissionDto;
import com.boxy.boxy.modules.administration.dto.RoleDto;
import com.boxy.boxy.modules.administration.entity.Permission;
import com.boxy.boxy.modules.administration.entity.Role;
import com.boxy.boxy.modules.administration.repository.PermissionRepository;
import com.boxy.boxy.modules.administration.repository.RoleRepository;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoleDto> getAllRoles() {
        Long companyId = SecurityUtils.getCurrentCompanyId();
        return roleRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDto getRoleById(Long id) {
        Role role = roleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
        return toDto(role);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getRoleMembers(Long roleId) {
        return userRepository.findAll().stream()
                .map(u -> Map.of(
                        "fullName", u.getFullName() != null ? u.getFullName() : u.getUsername(),
                        "email", u.getEmail() != null ? u.getEmail() : ""
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getAllPermissions() {
        return permissionRepository.findAllByOrderByModuleAscActionAsc().stream()
                .map(this::toPermissionDto)
                .toList();
    }

    private RoleDto toDto(Role role) {
        List<PermissionDto> permissionDtos = role.getPermissions().stream()
                .map(this::toPermissionDto)
                .toList();

        return RoleDto.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .label(role.getName())
                .description(role.getDescription())
                .isSystem(Boolean.TRUE.equals(role.getIsSystem()))
                .userCount("ROLE_SUPER_ADMIN".equals(role.getCode()) ? 1L : 0L)
                .permissions(permissionDtos)
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
