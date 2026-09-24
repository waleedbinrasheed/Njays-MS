package com.inventory.dto.auth;

import com.inventory.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username or email is required") @Size(max = 150) String username,
            @NotBlank(message = "Password is required") @Size(max = 200) String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]"; // never log the password
        }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") @Size(max = 200) String currentPassword,
            @NotBlank(message = "New password is required") @StrongPassword String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record CurrentUserResponse(
            Long id,
            String username,
            String fullName,
            String email,
            List<String> roles,
            List<String> permissions,
            boolean mustChangePassword,
            long sessionTimeoutSeconds) {
    }
}
