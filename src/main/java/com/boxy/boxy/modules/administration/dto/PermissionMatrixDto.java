package com.boxy.boxy.modules.administration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /administration/permissions/matrix} — the full catalog of permissions the
 * company can grant, plus each role's current grants. Returns raw permission codes (not
 * FE-style "cells" like CRUD/View) so the API stays lossless: converting a level set into
 * one of a handful of named cells is the frontend's presentation concern, not the backend's.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionMatrixDto {
    private List<PermissionDto> catalog;
    private List<RolePermissionsDto> roles;
}
