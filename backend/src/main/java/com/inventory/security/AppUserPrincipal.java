package com.inventory.security;

import com.inventory.entity.AppUser;
import com.inventory.entity.Permission;
import com.inventory.entity.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Snapshot of a user stored in the HTTP session. Authorities are the user's permission
 * codes plus ROLE_&lt;name&gt;. It is refreshed from the database on every request by
 * {@link SessionUserRefreshFilter}, so role or status changes apply immediately.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String fullName;
    private final String email;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Set<String> roles;
    private final Set<String> permissions;
    private String passwordHash;

    private AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.active = user.isActive();
        this.mustChangePassword = user.isMustChangePassword();
        this.passwordHash = user.getPasswordHash();
        this.roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
        this.permissions = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Requires roles and their permissions to be loaded. */
    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Stream.concat(permissions.stream(), roles.stream().map(r -> "ROLE_" + r))
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /** Same identity and same effective access (used to decide whether to rebuild the session token). */
    public boolean sameAccessAs(AppUserPrincipal other) {
        return id.equals(other.id) && roles.equals(other.roles) && permissions.equals(other.permissions)
                && mustChangePassword == other.mustChangePassword && active == other.active
                && fullName.equals(other.fullName);
    }

    public List<String> sortedPermissions() {
        return List.copyOf(permissions);
    }
}
