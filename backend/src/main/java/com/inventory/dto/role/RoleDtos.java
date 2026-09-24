package com.inventory.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record PermissionResponse(String code, String module, String description) {
    }

    public record RoleResponse(
            Long id,
            String name,
            String description,
            boolean systemRole,
            List<String> permissions,
            long userCount,
            Instant updatedAt) {
    }

    public record RoleRequest(
            @NotBlank(message = "Role name is required")
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_ ]{1,49}$",
                    message = "Role name must be 2-50 characters: letters, numbers, spaces or underscores")
            String name,
            @Size(max = 255) String description,
            @NotNull(message = "Permissions are required") Set<String> permissions) {
    }
}
