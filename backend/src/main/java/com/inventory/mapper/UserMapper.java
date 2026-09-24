package com.inventory.mapper;

import com.inventory.dto.auth.AuthDtos.CurrentUserResponse;
import com.inventory.dto.user.UserDtos.RoleRef;
import com.inventory.dto.user.UserDtos.UserResponse;
import com.inventory.entity.AppUser;
import com.inventory.security.AppUserPrincipal;

import java.util.Comparator;
import java.util.List;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(AppUser u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getMobile(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getRoles().stream()
                        .map(r -> new RoleRef(r.getId(), r.getName()))
                        .sorted(Comparator.comparing(RoleRef::name))
                        .toList(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }

    public static CurrentUserResponse toCurrentUser(AppUserPrincipal p, long sessionTimeoutSeconds) {
        return new CurrentUserResponse(
                p.getId(),
                p.getUsername(),
                p.getFullName(),
                p.getEmail(),
                List.copyOf(p.getRoles()),
                p.sortedPermissions(),
                p.isMustChangePassword(),
                sessionTimeoutSeconds);
    }
}
