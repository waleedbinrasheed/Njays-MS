package com.inventory.mapper;

import com.inventory.dto.role.RoleDtos.PermissionResponse;
import com.inventory.dto.role.RoleDtos.RoleResponse;
import com.inventory.entity.Permission;
import com.inventory.entity.Role;

public final class RoleMapper {

    private RoleMapper() {
    }

    public static RoleResponse toResponse(Role r, long userCount) {
        return new RoleResponse(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.isSystemRole(),
                r.getPermissions().stream().map(Permission::getCode).sorted().toList(),
                userCount,
                r.getUpdatedAt());
    }

    public static PermissionResponse toResponse(Permission p) {
        return new PermissionResponse(p.getCode(), p.getModule(), p.getDescription());
    }
}
