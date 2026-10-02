package com.inventory.auth;

import com.inventory.entity.Role;
import com.inventory.repository.RoleRepository;
import com.inventory.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleManagementIT extends ApiTestBase {

    @Autowired
    private RoleRepository roles;

    private MockHttpSession session;

    @BeforeEach
    void signIn() throws Exception {
        session = login(testUsers.admin().getUsername());
    }

    @Test
    void seededRolesAndPermissionsAreListed() throws Exception {
        mvc.perform(get("/api/roles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("ADMIN")))
                .andExpect(jsonPath("$[*].name", hasItem("NORMAL_USER")));

        mvc.perform(get("/api/permissions").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(18)))
                .andExpect(jsonPath("$[*].code", hasItem("SALE_CREATE")));
    }

    @Test
    void createRoleNormalizesNameAndRejectsDuplicates() throws Exception {
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "store  manager", "description", "Runs the shop",
                        "permissions", List.of("SALE_CREATE", "SALE_VIEW"))).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("STORE_MANAGER"))
                .andExpect(jsonPath("$.systemRole").value(false))
                .andExpect(jsonPath("$.permissions", hasSize(2)));

        mvc.perform(withJson(post("/api/roles"), Map.of("name", "Store Manager", "permissions", List.of()))
                        .session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownPermissionIsRejected() throws Exception {
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "Odd", "permissions", List.of("FLY_TO_MOON")))
                        .session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown permission(s): FLY_TO_MOON"));
    }

    @Test
    void builtInRolesCannotBeRenamedOrDeleted() throws Exception {
        Role normal = roles.findByName(Role.NORMAL_USER).orElseThrow();

        mvc.perform(withJson(put("/api/roles/" + normal.getId()), Map.of("name", "CLERK", "permissions", List.of()))
                        .session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/roles/" + normal.getId()).with(csrf()).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminRoleAlwaysKeepsAllPermissions() throws Exception {
        Role admin = roles.findByName(Role.ADMIN).orElseThrow();

        mvc.perform(withJson(put("/api/roles/" + admin.getId()), Map.of("name", "ADMIN", "description", "Owners",
                        "permissions", List.of())).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Owners"))
                .andExpect(jsonPath("$.permissions", hasSize(18)));
    }

    @Test
    void normalUserPermissionsCanBeChanged() throws Exception {
        Role normal = roles.findByName(Role.NORMAL_USER).orElseThrow();

        mvc.perform(withJson(put("/api/roles/" + normal.getId()), Map.of("name", "NORMAL_USER",
                        "permissions", List.of("SALE_VIEW", "SALE_CREATE", "STOCK_ADJUST"))).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasItem("STOCK_ADJUST")))
                .andExpect(jsonPath("$.permissions", hasSize(3)));
    }

    @Test
    void roleManagerCannotGrantPermissionsTheyLack() throws Exception {
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "ROLE_HELPER", "permissions", List.of("ROLE_MANAGE")))
                        .session(session))
                .andExpect(status().isCreated());
        MockHttpSession helper = login(testUsers.create("ROLE_HELPER").getUsername());
        Role helperRole = roles.findByName("ROLE_HELPER").orElseThrow();
        Role normal = roles.findByName(Role.NORMAL_USER).orElseThrow();

        mvc.perform(withJson(put("/api/roles/" + helperRole.getId()), Map.of("name", "ROLE_HELPER",
                        "permissions", List.of("ROLE_MANAGE", "USER_MANAGE"))).session(helper))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You cannot grant permissions you do not have"));
        mvc.perform(withJson(put("/api/roles/" + normal.getId()), Map.of("name", "NORMAL_USER",
                        "permissions", List.of())).session(helper))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleInUseCannotBeDeletedButUnusedRoleCan() throws Exception {
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "TEMP_ROLE", "permissions", List.of()))
                        .session(session))
                .andExpect(status().isCreated());
        Long tempId = roles.findByName("TEMP_ROLE").orElseThrow().getId();
        testUsers.create("TEMP_ROLE");

        mvc.perform(delete("/api/roles/" + tempId).with(csrf()).session(session))
                .andExpect(status().isConflict());

        mvc.perform(withJson(post("/api/roles"), Map.of("name", "UNUSED_ROLE", "permissions", List.of("SALE_VIEW")))
                        .session(session))
                .andExpect(status().isCreated());
        Long unusedId = roles.findByName("UNUSED_ROLE").orElseThrow().getId();
        mvc.perform(delete("/api/roles/" + unusedId).with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/roles/" + unusedId).session(session))
                .andExpect(status().isNotFound());
    }
}
