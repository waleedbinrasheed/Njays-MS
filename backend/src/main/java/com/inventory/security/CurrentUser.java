package com.inventory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the authenticated user for services and auditing. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static Optional<Long> id() {
        return principal().map(AppUserPrincipal::getId);
    }

    /** For operations that are only reachable when authenticated. */
    public static Long requireId() {
        return id().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
