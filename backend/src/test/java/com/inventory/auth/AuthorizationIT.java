package com.inventory.auth;

import com.inventory.entity.AppUser;
import com.inventory.repository.RoleRepository;
import com.inventory.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authorization is enforced by the backend, whatever the frontend shows or sends. */
class AuthorizationIT extends ApiTestBase {

    @Autowired
    private RoleRepository roles;

    @Test
    void anonymousCallersAreRejectedFromAdminApis() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void normalUserCannotCallAdminApisDirectly() throws Exception {
        AppUser normal = testUsers.normalUser();
        MockHttpSession session = login(normal.getUsername());

        mvc.perform(get("/api/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
        mvc.perform(get("/api/users/" + normal.getId()).session(session)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/users"), Map.of("username", "sneaky")).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/users/" + normal.getId()), Map.of("fullName", "x")).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/users/" + normal.getId() + "/reset-password"),
                        Map.of("newPassword", "Abcdef123")).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/roles").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/permissions").session(session)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "X", "permissions", List.of())).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/roles/1").with(csrf()).session(session)).andExpect(status().isForbidden());
    }

    @Test
    void normalUserCannotEscalateOwnRoles() throws Exception {
        AppUser normal = testUsers.normalUser();
        MockHttpSession session = login(normal.getUsername());
        Long adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();

        mvc.perform(withJson(put("/api/users/" + normal.getId()), Map.of(
                        "fullName", "Me", "roleIds", List.of(adminRoleId), "active", true)).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminApis() throws Exception {
        MockHttpSession session = login(testUsers.admin().getUsername());

        mvc.perform(get("/api/users").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/roles").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/permissions").session(session)).andExpect(status().isOk());
    }

    @Test
    void roleChangeAppliesToExistingSessionImmediately() throws Exception {
        AppUser normal = testUsers.normalUser();
        MockHttpSession normalSession = login(normal.getUsername());
        MockHttpSession adminSession = login(testUsers.admin().getUsername());
        mvc.perform(get("/api/users").session(normalSession)).andExpect(status().isForbidden());

        Long adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();
        mvc.perform(withJson(put("/api/users/" + normal.getId()), Map.of(
                        "fullName", normal.getFullName(), "roleIds", List.of(adminRoleId), "active", true))
                        .session(adminSession))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users").session(normalSession)).andExpect(status().isOk());
    }

    @Test
    void customRoleGrantsOnlyItsPermissions() throws Exception {
        MockHttpSession adminSession = login(testUsers.admin().getUsername());
        mvc.perform(withJson(post("/api/roles"), Map.of(
                        "name", "User Auditor", "permissions", List.of("USER_MANAGE"))).session(adminSession))
                .andExpect(status().isCreated());

        AppUser auditor = testUsers.create("USER_AUDITOR");
        MockHttpSession auditorSession = login(auditor.getUsername());

        mvc.perform(get("/api/users").session(auditorSession)).andExpect(status().isOk());
        mvc.perform(get("/api/permissions").session(auditorSession)).andExpect(status().isForbidden());
    }
}
