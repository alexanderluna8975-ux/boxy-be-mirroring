package com.boxy.boxy.core.security;

import com.boxy.boxy.modules.administration.dto.UserPermissionOverridesDto;
import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import com.boxy.boxy.modules.administration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username or email: " + identifier));

        List<String> rolesAndPermissions = new ArrayList<>();
        Long defaultBranchId = null;
        boolean isSuperAdmin = false;

        for (UserBranchRole ubr : user.getBranchRoles()) {
            if (ubr.getRole() != null) {
                rolesAndPermissions.add(ubr.getRole().getCode());
                if ("ROLE_SUPER_ADMIN".equalsIgnoreCase(ubr.getRole().getCode())) {
                    isSuperAdmin = true;
                }
                ubr.getRole().getPermissions().forEach(p -> rolesAndPermissions.add(p.getCode()));
            }
            if (Boolean.TRUE.equals(ubr.getIsDefault()) && ubr.getBranch() != null) {
                defaultBranchId = ubr.getBranch().getId();
            }
        }

        if (!isSuperAdmin && user.getPermissionOverrides() != null && !user.getPermissionOverrides().isBlank()) {
            try {
                UserPermissionOverridesDto overrides = objectMapper.readValue(user.getPermissionOverrides(), UserPermissionOverridesDto.class);
                if (overrides != null) {
                    if (overrides.getGranted() != null) {
                        for (String g : overrides.getGranted()) {
                            if (!rolesAndPermissions.contains(g)) {
                                rolesAndPermissions.add(g);
                            }
                        }
                    }
                    if (overrides.getRevoked() != null) {
                        rolesAndPermissions.removeAll(overrides.getRevoked());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (defaultBranchId == null && !user.getBranchRoles().isEmpty()) {
            defaultBranchId = user.getBranchRoles().get(0).getBranch().getId();
        }

        return UserPrincipal.create(
                user.getId(),
                user.getCompany() != null ? user.getCompany().getId() : null,
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                defaultBranchId,
                user.getStatus(),
                rolesAndPermissions
        );
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        return loadUserByUsername(user.getUsername());
    }
}