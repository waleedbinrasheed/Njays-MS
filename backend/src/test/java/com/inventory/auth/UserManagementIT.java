package com.inventory.auth;

import com.inventory.entity.AppUser;
import com.inventory.entity.Role;
import com.inventory.repository.RoleRepository;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIT extends ApiTestBase {

    @Autowired
    private RoleRepository roles;

    private AppUser admin;
    private MockHttpSession session;
    private Long normalRoleId;
    private Long adminRoleId;

    @BeforeEach
    void signIn() throws Exception {
        admin = testUsers.admin();
        session = login(admin.getUsername());
        normalRoleId = roles.findByName(Role.NORMAL_USER).orElseThrow().getId();
        adminRoleId = roles.findByName(Role.ADMIN).orElseThrow().getId();
    }

    @Test
    void createUserRequiresPasswordChangeAtFirstLogin() throws Exception {
        String username = TestUsers.uniqueName("cashier");
        mvc.perform(withJson(post("/api/users"), newUser(username, username + "@shop.test")).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.roles[0].name").value("NORMAL_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(get("/api/auth/me").session(login(username, "Welcome123")))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        String username = TestUsers.uniqueName("dup");
        mvc.perform(withJson(post("/api/users"), newUser(username, null)).session(session))
                .andExpect(status().isCreated());

        mvc.perform(withJson(post("/api/users"), newUser(username.toUpperCase(), null)).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username '" + username.toUpperCase() + "' is already taken"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        mvc.perform(withJson(post("/api/users"), newUser(TestUsers.uniqueName("u"), admin.getEmail().toUpperCase()))
                        .session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void createUserValidatesInput() throws Exception {
        Map<String, Object> bad = new HashMap<>();
        bad.put("username", "a b");
        bad.put("fullName", "");
        bad.put("email", "not-an-email");
        bad.put("password", "weak");
        bad.put("roleIds", List.of());
        mvc.perform(withJson(post("/api/users"), bad).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("roleIds")));
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        Map<String, Object> body = newUser(TestUsers.uniqueName("u"), null);
        body.put("roleIds", List.of(999999));
        mvc.perform(withJson(post("/api/users"), body).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("One or more selected roles do not exist"));
    }

    @Test
    void searchFiltersAndPaginates() throws Exception {
        AppUser inactive = testUsers.create(TestUsers.uniqueName("zzsearch"), Role.NORMAL_USER, false, false);

        mvc.perform(get("/api/users").param("search", inactive.getUsername()).param("active", "false").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(inactive.getUsername()));

        mvc.perform(get("/api/users").param("size", "1").param("sort", "createdAt,desc").session(session))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1));

        mvc.perform(get("/api/users").param("sort", "passwordHash").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotDeactivateThemselvesOrDropOwnAdminRole() throws Exception {
        mvc.perform(withJson(put("/api/users/" + admin.getId()), update(admin, List.of(adminRoleId), false)).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own account"));

        mvc.perform(withJson(put("/api/users/" + admin.getId()), update(admin, List.of(normalRoleId), true)).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot remove the ADMIN role from your own account"));
    }

    @Test
    void userManagerWithoutAdminRightsCannotEscalateOrTouchAdmins() throws Exception {
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "USER_HELPER", "permissions", List.of("USER_MANAGE")))
                        .session(session))
                .andExpect(status().isCreated());
        AppUser helperUser = testUsers.create("USER_HELPER");
        MockHttpSession helper = login(helperUser.getUsername());

        // Cannot grant ADMIN to themselves or to a new user.
        mvc.perform(withJson(put("/api/users/" + helperUser.getId()), update(helperUser, List.of(adminRoleId), true))
                        .session(helper))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot assign a role with permissions you do not have"));
        Map<String, Object> newAdmin = newUser(TestUsers.uniqueName("x"), null);
        newAdmin.put("roleIds", List.of(adminRoleId));
        mvc.perform(withJson(post("/api/users"), newAdmin).session(helper))
                .andExpect(status().isForbidden());

        // Cannot deactivate an admin or take over their account by resetting the password.
        mvc.perform(withJson(put("/api/users/" + admin.getId()), update(admin, List.of(adminRoleId), false)).session(helper))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot modify a user who has permissions you do not have"));
        mvc.perform(withJson(post("/api/users/" + admin.getId() + "/reset-password"), Map.of("newPassword", "Hijack123"))
                        .session(helper))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPasswordForcesChangeAtNextLogin() throws Exception {
        AppUser user = testUsers.normalUser();
        mvc.perform(withJson(post("/api/users/" + user.getId() + "/reset-password"), Map.of("newPassword", "Temp12345"))
                        .session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(login(user.getUsername(), "Temp12345")))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void unknownUserGives404() throws Exception {
        mvc.perform(get("/api/users/999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 999999"));
    }

    private Map<String, Object> newUser(String username, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("fullName", "New Person");
        body.put("email", email);
        body.put("mobile", "0300-1234567");
        body.put("password", "Welcome123");
        body.put("roleIds", List.of(normalRoleId));
        return body;
    }

    private Map<String, Object> update(AppUser user, List<Long> roleIds, boolean active) {
        return Map.of("fullName", user.getFullName(), "roleIds", roleIds, "active", active);
    }
}
