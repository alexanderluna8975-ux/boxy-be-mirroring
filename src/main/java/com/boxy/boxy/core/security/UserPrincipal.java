package com.boxy.boxy.core.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private final Long id;
    private final Long companyId;
    private final String username;
    private final String email;
    private final String password;
    private final String fullName;
    private final Long activeBranchId;
    private final String status;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(Long id, Long companyId, String username, String email, String password, String fullName, Long activeBranchId, String status, List<String> rolesAndPermissions) {
        List<SimpleGrantedAuthority> authorities = rolesAndPermissions.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        return UserPrincipal.builder()
                .id(id)
                .companyId(companyId)
                .username(username)
                .email(email)
                .password(password)
                .fullName(fullName)
                .activeBranchId(activeBranchId)
                .status(status)
                .authorities(authorities)
                .build();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
