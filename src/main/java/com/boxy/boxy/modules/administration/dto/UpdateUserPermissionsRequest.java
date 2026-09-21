package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPermissionsRequest {
    private UserPermissionOverridesDto overrides;
    private List<String> granted;
    private List<String> revoked;

    public UserPermissionOverridesDto resolveOverrides() {
        if (overrides != null) {
            return UserPermissionOverridesDto.builder()
                    .granted(overrides.getGranted() != null ? overrides.getGranted() : Collections.emptyList())
                    .revoked(overrides.getRevoked() != null ? overrides.getRevoked() : Collections.emptyList())
                    .build();
        }
        return UserPermissionOverridesDto.builder()
                .granted(granted != null ? granted : Collections.emptyList())
                .revoked(revoked != null ? revoked : Collections.emptyList())
                .build();
    }
}
