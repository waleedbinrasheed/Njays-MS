package com.inventory.support;

import com.inventory.entity.AppUser;
import com.inventory.entity.Role;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.RoleRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

/** Creates users directly in the database for tests. */
@TestComponent
public class TestUsers {

    public static final String PASSWORD = "Secret123";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public TestUsers(AppUserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    public AppUser admin() {
        return create(Role.ADMIN);
    }

    public AppUser normalUser() {
        return create(Role.NORMAL_USER);
    }

    public AppUser create(String roleName) {
        return create(uniqueName(roleName.toLowerCase()), roleName, true, false);
    }

    public AppUser create(String username, String roleName, boolean active, boolean mustChangePassword) {
        Role role = roles.findByName(roleName).orElseThrow();
        AppUser user = new AppUser(username, encoder.encode(PASSWORD), "Test " + username);
        user.setEmail(username + "@example.test");
        user.setActive(active);
        user.setMustChangePassword(mustChangePassword);
        user.replaceRoles(Set.of(role));
        return users.saveAndFlush(user);
    }

    public static String uniqueName(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().substring(0, 8)).replace("-", "");
    }
}
