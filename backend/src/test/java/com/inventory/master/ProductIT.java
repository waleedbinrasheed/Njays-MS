package com.inventory.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductIT extends ApiTestBase {

    private MockHttpSession admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = login(testUsers.admin().getUsername());
    }

    @Test
    void createProductNormalisesIsbnAndLinksPublisherAndCategory() throws Exception {
        long publisherId = createPublisher(TestUsers.uniqueName("Pub"));
        long categoryId = createCategory(TestUsers.uniqueName("Cat"));
        Map<String, Object> body = product("978-0-306-40615-7", "Clean Code");
        body.put("publisherId", publisherId);
        body.put("categoryId", categoryId);

        mvc.perform(withJson(post("/api/products"), body).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("9780306406157"))
                .andExpect(jsonPath("$.productType").value("BOOK"))
                .andExpect(jsonPath("$.publisher.id").value(publisherId))
                .andExpect(jsonPath("$.category.id").value(categoryId))
                .andExpect(jsonPath("$.sellingPrice").value(1500.00))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void duplicateIsbnIsRejectedEvenWhenFormattedDifferently() throws Exception {
        mvc.perform(withJson(post("/api/products"), product("0-306-40615-2", "First")).session(admin))
                .andExpect(status().isCreated());

        mvc.perform(withJson(post("/api/products"), product("0306406152", "Second")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A product with ISBN 0306406152 already exists"));
    }

    @Test
    void productsWithoutIsbnAreAllowed() throws Exception {
        mvc.perform(withJson(post("/api/products"), product(null, "No ISBN A")).session(admin)).andExpect(status().isCreated());
        mvc.perform(withJson(post("/api/products"), product("", "No ISBN B")).session(admin)).andExpect(status().isCreated());
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        Map<String, Object> body = product("12345", "");
        body.put("sellingPrice", 0);
        body.put("purchasePrice", -1);
        body.put("publicationYear", 3000);

        mvc.perform(withJson(post("/api/products"), body).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("isbn")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("sellingPrice")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("purchasePrice")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("publicationYear")));
    }

    @Test
    void inactivePublisherCannotBeAssigned() throws Exception {
        long publisherId = createPublisher(TestUsers.uniqueName("Old"));
        JsonNode pub = read(mvc.perform(get("/api/publishers/" + publisherId).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/publishers/" + publisherId), Map.of(
                        "name", pub.get("name").asText(), "active", false, "version", pub.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isOk());

        Map<String, Object> body = product(null, "Orphan");
        body.put("publisherId", publisherId);
        mvc.perform(withJson(post("/api/products"), body).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("is inactive")));
    }

    @Test
    void searchMatchesIsbnTitleAndAuthorAndFilters() throws Exception {
        String tag = TestUsers.uniqueName("zq");
        Map<String, Object> body = product("9781234567897", "The " + tag + " Handbook");
        body.put("author", "Ayesha Khan");
        long categoryId = createCategory(TestUsers.uniqueName("Cat"));
        body.put("categoryId", categoryId);
        mvc.perform(withJson(post("/api/products"), body).session(admin)).andExpect(status().isCreated());

        for (String term : new String[]{tag, "978-1234-567897", "ayesha kh"}) {
            mvc.perform(get("/api/products").param("search", term).param("categoryId", String.valueOf(categoryId)).session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get("/api/products").param("search", tag).param("active", "false").session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void staleUpdateIsRejected() throws Exception {
        JsonNode created = read(mvc.perform(withJson(post("/api/products"), product(null, "Versioned")).session(admin))
                .andReturn());
        long id = created.get("id").asLong();

        Map<String, Object> update = product(null, "Versioned v2");
        update.put("version", 0);
        mvc.perform(withJson(put("/api/products/" + id), update).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        update.put("title", "Someone else's older edit");
        mvc.perform(withJson(put("/api/products/" + id), update).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This record was changed by someone else. Reload it and try again"));
    }

    @Test
    void normalUserCanViewButNotChangeProducts() throws Exception {
        MockHttpSession normal = login(testUsers.normalUser().getUsername());

        mvc.perform(get("/api/products").session(normal)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/products"), product(null, "Nope")).session(normal))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/products/1"), product(null, "Nope")).session(normal))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/1").with(csrf()).session(normal)).andExpect(status().isForbidden());
    }

    @Test
    void unusedProductCanBeDeleted() throws Exception {
        long id = read(mvc.perform(withJson(post("/api/products"), product(null, "Temporary")).session(admin)).andReturn())
                .get("id").asLong();
        mvc.perform(delete("/api/products/" + id).with(csrf()).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + id).session(admin)).andExpect(status().isNotFound());
    }

    private Map<String, Object> product(String isbn, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("isbn", isbn);
        body.put("title", title);
        body.put("purchasePrice", 1000.00);
        body.put("sellingPrice", 1500.00);
        return body;
    }

    private long createPublisher(String name) throws Exception {
        return read(mvc.perform(withJson(post("/api/publishers"), Map.of("name", name)).session(admin))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long createCategory(String name) throws Exception {
        return read(mvc.perform(withJson(post("/api/categories"), Map.of("name", name)).session(admin))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
}
