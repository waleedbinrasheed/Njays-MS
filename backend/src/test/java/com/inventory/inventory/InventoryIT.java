package com.inventory.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIT extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ZoneId businessZone;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private MockHttpSession admin;
    private long adminId;

    @BeforeEach
    void signIn() throws Exception {
        var user = testUsers.admin();
        adminId = user.getId();
        admin = login(user.getUsername());
    }

    @Test
    void newProductStartsWithZeroStock() throws Exception {
        long id = createProduct("Fresh");
        mvc.perform(get("/api/inventory/" + id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.reorderLevel").value(0))
                .andExpect(jsonPath("$.stockLevel").value("OUT_OF_STOCK"));
    }

    @Test
    void adjustmentUpdatesStockAndWritesLedgerEntry() throws Exception {
        long id = createProduct("Adjust me");

        JsonNode adj = read(adjust(id, 10, 0, "FOUND", "Box found in store room")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.previousQuantity").value(0))
                .andExpect(jsonPath("$.adjustmentQuantity").value(10))
                .andExpect(jsonPath("$.resultingQuantity").value(10))
                .andReturn());

        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(id)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].quantity").value(10))
                .andExpect(jsonPath("$.content[0].previousQuantity").value(0))
                .andExpect(jsonPath("$.content[0].resultingQuantity").value(10))
                .andExpect(jsonPath("$.content[0].referenceType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.content[0].referenceId").value(adj.get("id").asLong()))
                .andExpect(jsonPath("$.content[0].user.id").value(adminId))
                .andExpect(jsonPath("$.content[0].notes").value("Found: Box found in store room"));
        assertLedgerMatchesStock(id);
    }

    @Test
    void physicalCountCorrectionFromTheSpec() throws Exception {
        long id = createProduct("Counted");
        adjust(id, 10, 0, "FOUND", null).andExpect(status().isCreated());

        // System says 10, shelf has 8: adjustment of -2.
        adjust(id, -2, 10, "COUNT_CORRECTION", "Shelf count").andExpect(status().isCreated());

        mvc.perform(get("/api/inventory/" + id).session(admin)).andExpect(jsonPath("$.quantity").value(8));
        assertLedgerMatchesStock(id);
    }

    @Test
    void stockCannotGoNegativeAndNothingIsWrittenOnFailure() throws Exception {
        long id = createProduct("Scarce");
        adjust(id, 3, 0, "FOUND", null).andExpect(status().isCreated());

        adjust(id, -5, 3, "LOST", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Scarce': available 3, requested 5"));

        mvc.perform(get("/api/inventory/" + id).session(admin)).andExpect(jsonPath("$.quantity").value(3));
        assertThat(count("select count(*) from stock_transaction where product_id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from stock_adjustment where product_id = ?", id)).isEqualTo(1);
    }

    @Test
    void adjustmentAgainstOutdatedQuantityIsRejected() throws Exception {
        long id = createProduct("Moving");
        adjust(id, 5, 0, "FOUND", null).andExpect(status().isCreated());

        adjust(id, -1, 0, "DAMAGED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("changed to 5")));
    }

    @Test
    void adjustmentValidation() throws Exception {
        long id = createProduct("Rules");
        adjust(id, 0, 0, "FOUND", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Adjustment quantity must not be zero"));
        adjust(id, 1, 0, "OTHER", "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Notes are required when the reason is 'Other'"));
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", id)).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("reason")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("adjustmentQuantity")));
        adjust(999_999, 1, 0, "FOUND", null).andExpect(status().isNotFound());
    }

    @Test
    void lowAndOutOfStockFiltersAndReorderLevel() throws Exception {
        String tag = TestUsers.uniqueName("lvl");
        long low = createProduct(tag + " low");
        long ok = createProduct(tag + " ok");
        long out = createProduct(tag + " out");
        adjust(low, 3, 0, "FOUND", null);
        adjust(ok, 20, 0, "FOUND", null);

        for (long id : new long[]{low, ok, out}) {
            mvc.perform(withJson(put("/api/inventory/" + id + "/reorder-level"), Map.of("reorderLevel", 5)).session(admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reorderLevel").value(5));
        }
        mvc.perform(get("/api/inventory/" + low).session(admin)).andExpect(jsonPath("$.stockLevel").value("LOW"));
        mvc.perform(get("/api/inventory/" + ok).session(admin)).andExpect(jsonPath("$.stockLevel").value("OK"));

        mvc.perform(get("/api/inventory").param("search", tag).param("status", "LOW").session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].quantity", everyItem(lessThanOrEqualTo(5))));
        mvc.perform(get("/api/inventory").param("search", tag).param("status", "OUT").session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(out));
        mvc.perform(get("/api/inventory").param("search", tag).param("sort", "quantity,desc").session(admin))
                .andExpect(jsonPath("$.content[0].productId").value(ok));

        mvc.perform(withJson(put("/api/inventory/" + low + "/reorder-level"), Map.of("reorderLevel", -1)).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryReflectsStockChanges() throws Exception {
        JsonNode before = read(mvc.perform(get("/api/inventory/summary").session(admin)).andReturn());
        long id = createProduct("Counted in summary");
        adjust(id, 7, 0, "FOUND", null);

        mvc.perform(get("/api/inventory/summary").session(admin))
                .andExpect(jsonPath("$.totalProducts").value(before.get("totalProducts").asLong() + 1))
                .andExpect(jsonPath("$.totalQuantity").value(before.get("totalQuantity").asLong() + 7));
    }

    @Test
    void movementsFilterByTypeAndBusinessDate() throws Exception {
        long id = createProduct("Dated");
        adjust(id, 4, 0, "FOUND", null);
        LocalDate today = LocalDate.now(businessZone);

        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(id))
                        .param("type", "ADJUSTMENT").param("from", today.toString()).param("to", today.toString())
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(id))
                        .param("to", today.minusDays(1).toString()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(id)).param("type", "SALE").session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/stock-transactions").param("from", today.toString())
                        .param("to", today.minusDays(3).toString()).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productWithStockHistoryCannotBeDeletedButUnusedOneCan() throws Exception {
        long used = createProduct("Has history");
        adjust(used, 1, 0, "FOUND", null);
        mvc.perform(delete("/api/products/" + used).with(csrf()).session(admin))
                .andExpect(status().isConflict());

        long unused = createProduct("Never stocked");
        mvc.perform(delete("/api/products/" + unused).with(csrf()).session(admin)).andExpect(status().isNoContent());
        assertThat(count("select count(*) from inventory where product_id = ?", unused)).isZero();
    }

    @Test
    void normalUserCanViewStockButNotChangeIt() throws Exception {
        long id = createProduct("Visible");
        MockHttpSession normal = login(testUsers.normalUser().getUsername());

        mvc.perform(get("/api/inventory").session(normal)).andExpect(status().isOk());
        mvc.perform(get("/api/stock-transactions").session(normal)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/stock-adjustments"), adjustment(id, 5, 0, "FOUND", null)).session(normal))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/inventory/" + id + "/reorder-level"), Map.of("reorderLevel", 1)).session(normal))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryHasNoDirectWriteEndpoint() throws Exception {
        long id = createProduct("No shortcuts");
        mvc.perform(withJson(put("/api/inventory/" + id), Map.of("quantity", 999)).session(admin))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/inventory/" + id).session(admin)).andExpect(jsonPath("$.quantity").value(0));
    }

    // --- helpers ---

    private long createProduct(String title) throws Exception {
        return read(mvc.perform(withJson(post("/api/products"),
                        Map.of("title", title, "purchasePrice", 100, "sellingPrice", 150)).session(admin))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private Map<String, Object> adjustment(long productId, int qty, int expected, String reason, String notes) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("adjustmentQuantity", qty);
        body.put("expectedCurrentQuantity", expected);
        body.put("reason", reason);
        body.put("notes", notes);
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions adjust(long productId, int qty, int expected,
                                                                     String reason, String notes) throws Exception {
        return mvc.perform(withJson(post("/api/stock-adjustments"), adjustment(productId, qty, expected, reason, notes))
                .session(admin));
    }

    /** Raw SQL checks must see pending JPA changes (tests share one transaction). */
    private long count(String sql, Object... args) {
        entityManager.flush();
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private void assertLedgerMatchesStock(long productId) {
        long stock = count("select quantity from inventory where product_id = ?", productId);
        long ledger = count("select coalesce(sum(quantity), 0) from stock_transaction where product_id = ?", productId);
        assertThat(ledger).isEqualTo(stock);
    }
}
