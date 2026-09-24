package com.inventory.dto.user;

import com.inventory.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {
    }

    public record RoleRef(Long id, String name) {
    }

    public record UserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            String mobile,
            boolean active,
            boolean mustChangePassword,
            List<RoleRef> roles,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
                    message = "Username must be 3-50 characters: letters, numbers, dot, dash or underscore")
            String username,
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Pattern(regexp = "^[0-9+()\\- ]{0,30}$", message = "Mobile number is not valid") String mobile,
            @NotBlank(message = "Password is required") @StrongPassword String password,
            @NotEmpty(message = "At least one role is required") Set<Long> roleIds,
            Boolean active) {

        @Override
        public String toString() {
            return "CreateUserRequest[username=" + username + "]";
        }
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Full name is required") @Size(max = 100) String fullName,
            @Email(message = "Email address is not valid") @Size(max = 150) String email,
            @Pattern(regexp = "^[0-9+()\\- ]{0,30}$", message = "Mobile number is not valid") String mobile,
            @NotEmpty(message = "At least one role is required") Set<Long> roleIds,
            boolean active) {
    }

    /** Admin-set temporary password; the user must change it at next login. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Password is required") @StrongPassword String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }
}
