package com.boxy.boxy.core.security;

import com.boxy.boxy.modules.administration.entity.User;
import com.boxy.boxy.modules.administration.entity.UserBranchRole;
import com.boxy.boxy.modules.administration.repository.UserRepository;
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

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithRolesAndPermissions(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        List<String> rolesAndPermissions = new ArrayList<>();
        String defaultBranchId = null;

        for (UserBranchRole ubr : user.getBranchRoles()) {
            if (ubr.getRole() != null) {
                rolesAndPermissions.add(ubr.getRole().getCode());
                ubr.getRole().getPermissions().forEach(p -> rolesAndPermissions.add(p.getCode()));
            }
            if (Boolean.TRUE.equals(ubr.getIsDefault()) && ubr.getBranch() != null) {
                defaultBranchId = ubr.getBranch().getId();
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
                rolesAndPermissions
        );
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(String id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        return loadUserByUsername(user.getUsername());
    }
}
