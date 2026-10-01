package com.inventory.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publishers, categories, suppliers and customers. */
class MasterDataIT extends ApiTestBase {

    private MockHttpSession admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = login(testUsers.admin().getUsername());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/publishers", "/api/categories", "/api/suppliers"})
    void namesMustBeUniqueIgnoringCase(String path) throws Exception {
        String name = TestUsers.uniqueName("Name");
        mvc.perform(withJson(post(path), Map.of("name", name)).session(admin)).andExpect(status().isCreated());

        mvc.perform(withJson(post(path), Map.of("name", "  " + name.toUpperCase() + " ")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/publishers", "/api/categories", "/api/suppliers", "/api/customers"})
    void crudSearchAndDelete(String path) throws Exception {
        String name = TestUsers.uniqueName("Item");
        JsonNode created = read(mvc.perform(withJson(post(path), withRep(path, Map.of("name", name, "email", "a@b.pk")))
                        .session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn());
        long id = created.get("id").asLong();

        mvc.perform(get(path).param("search", name.toLowerCase()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(withJson(put(path + "/" + id), withRep(path, Map.of("name", name + " renamed", "active", false, "version", 0)))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(path).param("search", name.toLowerCase()).param("active", "true").session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(delete(path + "/" + id).with(csrf()).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get(path + "/" + id).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void emailAndPhoneAreValidated() throws Exception {
        mvc.perform(withJson(post("/api/suppliers"), Map.of("name", "X", "email", "nope", "phone", "call me")).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void publisherOrCategoryInUseCannotBeDeleted() throws Exception {
        long publisherId = read(mvc.perform(withJson(post("/api/publishers"), Map.of("name", TestUsers.uniqueName("P")))
                .session(admin)).andReturn()).get("id").asLong();
        long categoryId = read(mvc.perform(withJson(post("/api/categories"), Map.of("name", TestUsers.uniqueName("C")))
                .session(admin)).andReturn()).get("id").asLong();
        mvc.perform(withJson(post("/api/products"), Map.of("title", "Uses both", "purchasePrice", 1, "sellingPrice", 2,
                        "publisherId", publisherId, "categoryId", categoryId)).session(admin))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/publishers/" + publisherId).with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("Deactivate it instead")));
        mvc.perform(delete("/api/categories/" + categoryId).with(csrf()).session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void walkInCustomerExistsAndIsProtected() throws Exception {
        JsonNode walkIn = read(mvc.perform(get("/api/customers/walk-in").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Walk-in Customer"))
                .andExpect(jsonPath("$.walkIn").value(true))
                .andReturn());
        long id = walkIn.get("id").asLong();

        mvc.perform(withJson(put("/api/customers/" + id), Map.of("name", "Walk-in Customer", "active", false,
                        "version", walkIn.get("version").asLong())).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The walk-in customer cannot be deactivated"));
        mvc.perform(delete("/api/customers/" + id).with(csrf()).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The walk-in customer cannot be deleted"));
    }

    @Test
    void customersWithTheSameNameAreAllowed() throws Exception {
        Map<String, Object> ali = Map.of("name", "Ali Raza", "representativeId", counterRepresentativeId());
        mvc.perform(withJson(post("/api/customers"), ali).session(admin)).andExpect(status().isCreated());
        mvc.perform(withJson(post("/api/customers"), ali).session(admin)).andExpect(status().isCreated());
    }

    @Test
    void updateWithoutVersionIsRejected() throws Exception {
        long id = read(mvc.perform(withJson(post("/api/categories"), Map.of("name", TestUsers.uniqueName("V")))
                .session(admin)).andReturn()).get("id").asLong();
        mvc.perform(withJson(put("/api/categories/" + id), Map.of("name", "whatever")).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Version is required when updating a record"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/publishers", "/api/categories", "/api/suppliers", "/api/customers"})
    void normalUserCanViewButNotChange(String path) throws Exception {
        MockHttpSession normal = login(testUsers.normalUser().getUsername());
        mvc.perform(get(path).session(normal)).andExpect(status().isOk());
        mvc.perform(withJson(post(path), Map.of("name", "Nope")).session(normal)).andExpect(status().isForbidden());
        mvc.perform(delete(path + "/1").with(csrf()).session(normal)).andExpect(status().isForbidden());
    }

    /** Customers must name their representative; other master data does not have one. */
    private Map<String, Object> withRep(String path, Map<String, Object> body) {
        if (!path.equals("/api/customers")) return body;
        Map<String, Object> copy = new java.util.HashMap<>(body);
        copy.put("representativeId", counterRepresentativeId());
        return copy;
    }

    @Test
    void anonymousCannotReadMasterData() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
    }
}
