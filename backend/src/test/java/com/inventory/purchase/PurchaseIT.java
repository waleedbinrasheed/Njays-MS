package com.inventory.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PurchaseIT extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ZoneId businessZone;

    private MockHttpSession admin;
    private long supplierId;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(testUsers.admin().getUsername());
        supplierId = create("/api/suppliers", Map.of("name", TestUsers.uniqueName("Supplier")));
        today = LocalDate.now(businessZone);
    }

    @Test
    void purchaseAddsStockAndRecordsLedgerAndTotals() throws Exception {
        long a = product("Book A");
        long b = product("Book B");

        JsonNode p = read(createPurchase(List.of(line(a, 10, "250.00", "10", "0"), line(b, 4, "300.00", "0", "5")), "50")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseNumber", matchesPattern("PUR-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.subtotal").value(3700.00))       // 2500 + 1200
                .andExpect(jsonPath("$.discountTotal").value(300.00))   // 250 line + 50 invoice
                .andExpect(jsonPath("$.taxTotal").value(60.00))         // 5% of 1200
                .andExpect(jsonPath("$.total").value(3460.00))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(2250.00))
                .andExpect(jsonPath("$.createdBy.name").exists())
                .andReturn());

        assertThat(stock(a)).isEqualTo(10);
        assertThat(stock(b)).isEqualTo(4);
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(a)).session(admin))
                .andExpect(jsonPath("$.content[0].type").value("PURCHASE"))
                .andExpect(jsonPath("$.content[0].quantity").value(10))
                .andExpect(jsonPath("$.content[0].referenceType").value("PURCHASE"))
                .andExpect(jsonPath("$.content[0].referenceId").value(p.get("id").asLong()))
                .andExpect(jsonPath("$.content[0].notes").value(p.get("purchaseNumber").asText()));
    }

    @Test
    void purchaseNumbersAreSequential() throws Exception {
        long a = product("Numbered");
        String first = read(createPurchase(List.of(line(a, 1, "10", null, null)), null).andReturn()).get("purchaseNumber").asText();
        String second = read(createPurchase(List.of(line(a, 1, "10", null, null)), null).andReturn()).get("purchaseNumber").asText();
        assertThat(sequenceOf(second)).isEqualTo(sequenceOf(first) + 1);
        assertThat(first).startsWith("PUR-" + today.getYear() + "-");
    }

    @Test
    void validationRules() throws Exception {
        long a = product("Valid");
        long inactive = product("Retired");
        deactivateProduct(inactive);

        createPurchase(List.of(), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
        createPurchase(List.of(line(a, 0, "10", null, null)), null).andExpect(status().isBadRequest());
        createPurchase(List.of(line(a, 1, "-1", null, null)), null).andExpect(status().isBadRequest());
        createPurchase(List.of(line(a, 1, "10", "101", null)), null).andExpect(status().isBadRequest());
        createPurchase(List.of(line(a, 1, "10", null, null), line(a, 2, "10", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than one line")));
        createPurchase(List.of(line(inactive, 1, "10", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive")));
        createPurchase(List.of(line(999_999, 1, "10", null, null)), null).andExpect(status().isBadRequest());

        Map<String, Object> future = purchase(List.of(line(a, 1, "10", null, null)), null);
        future.put("purchaseDate", today.plusDays(1).toString());
        mvc.perform(withJson(post("/api/purchases"), future).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Purchase date cannot be in the future"));

        assertThat(stock(a)).isZero();
    }

    @Test
    void inactiveSupplierIsRejected() throws Exception {
        long a = product("For inactive supplier");
        JsonNode s = read(mvc.perform(get("/api/suppliers/" + supplierId).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/suppliers/" + supplierId), Map.of("name", s.get("name").asText(), "active", false,
                "version", s.get("version").asLong())).session(admin)).andExpect(status().isOk());

        createPurchase(List.of(line(a, 1, "10", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));
    }

    @Test
    void invoiceDiscountCannotExceedTotal() throws Exception {
        long a = product("Discounted");
        createPurchase(List.of(line(a, 1, "100", null, null)), "100.01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be more than")));
    }

    @Test
    void cancelReversesStock() throws Exception {
        long a = product("To cancel");
        long id = read(createPurchase(List.of(line(a, 6, "10", null, null)), null).andReturn()).get("id").asLong();

        mvc.perform(withJson(post("/api/purchases/" + id + "/cancel"), Map.of("reason", "Wrong supplier")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Wrong supplier"))
                .andExpect(jsonPath("$.cancelledBy.name").exists());
        assertThat(stock(a)).isZero();
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(a)).param("type", "PURCHASE_CANCEL")
                        .session(admin))
                .andExpect(jsonPath("$.content[0].quantity").value(-6))
                .andExpect(jsonPath("$.content[0].resultingQuantity").value(0));

        mvc.perform(withJson(post("/api/purchases/" + id + "/cancel"), Map.of("reason", "Again")).session(admin))
                .andExpect(status().isConflict());
        mvc.perform(withJson(put("/api/purchases/" + id + "/payment-status"), Map.of("paymentStatus", "PAID")).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCancelWhenStockAlreadyUsed() throws Exception {
        long a = product("Partly used");
        long id = read(createPurchase(List.of(line(a, 5, "10", null, null)), null).andReturn()).get("id").asLong();
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", a, "adjustmentQuantity", -3,
                "expectedCurrentQuantity", 5, "reason", "DAMAGED")).session(admin)).andExpect(status().isCreated());

        mvc.perform(withJson(post("/api/purchases/" + id + "/cancel"), Map.of("reason", "Mistake")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already been used")))
                .andExpect(jsonPath("$.message", containsString("available 2, requested 5")));
        assertThat(stock(a)).isEqualTo(2);
    }

    @Test
    void cancelRequiresReason() throws Exception {
        long a = product("Reasonless");
        long id = read(createPurchase(List.of(line(a, 1, "10", null, null)), null).andReturn()).get("id").asLong();
        mvc.perform(withJson(post("/api/purchases/" + id + "/cancel"), Map.of("reason", " ")).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentStatusCanBeUpdated() throws Exception {
        long a = product("Pay later");
        long id = read(createPurchase(List.of(line(a, 1, "10", null, null)), null).andReturn()).get("id").asLong();
        mvc.perform(withJson(put("/api/purchases/" + id + "/payment-status"), Map.of("paymentStatus", "PARTIAL")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"));
    }

    @Test
    void historyFilters() throws Exception {
        long a = product("Filtered");
        long other = product("Other");
        String number = read(createPurchase(List.of(line(a, 2, "10", null, null)), null).andReturn()).get("purchaseNumber").asText();
        createPurchase(List.of(line(other, 1, "10", null, null)), null);

        mvc.perform(get("/api/purchases").param("supplierId", String.valueOf(supplierId)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/purchases").param("productId", String.valueOf(a)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].purchaseNumber").value(number));
        mvc.perform(get("/api/purchases").param("search", number).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].createdBy.name").exists());
        mvc.perform(get("/api/purchases").param("supplierId", String.valueOf(supplierId))
                        .param("from", today.plusDays(1).toString()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/purchases").param("supplierId", String.valueOf(supplierId)).param("paymentStatus", "PAID")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void supplierWithPurchasesCannotBeDeleted() throws Exception {
        createPurchase(List.of(line(product("Keeps supplier"), 1, "10", null, null)), null).andExpect(status().isCreated());
        mvc.perform(delete("/api/suppliers/" + supplierId).with(csrf()).session(admin)).andExpect(status().isConflict());
    }

    @Test
    void permissions() throws Exception {
        long a = product("Permission");
        long id = read(createPurchase(List.of(line(a, 1, "10", null, null)), null).andReturn()).get("id").asLong();
        MockHttpSession normal = login(testUsers.normalUser().getUsername());

        mvc.perform(get("/api/purchases").session(normal)).andExpect(status().isOk());
        mvc.perform(get("/api/purchases/" + id).session(normal)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/purchases"), purchase(List.of(line(a, 1, "10", null, null)), null)).session(normal))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/purchases/" + id + "/cancel"), Map.of("reason", "x")).session(normal))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(put("/api/purchases/" + id + "/payment-status"), Map.of("paymentStatus", "PAID")).session(normal))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private ResultActions createPurchase(List<Map<String, Object>> lines, String invoiceDiscount) throws Exception {
        return mvc.perform(withJson(post("/api/purchases"), purchase(lines, invoiceDiscount)).session(admin));
    }

    private Map<String, Object> purchase(List<Map<String, Object>> lines, String invoiceDiscount) {
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", supplierId);
        body.put("purchaseDate", today.toString());
        body.put("paymentStatus", "UNPAID");
        body.put("invoiceDiscount", invoiceDiscount);
        body.put("items", new ArrayList<>(lines));
        return body;
    }

    private Map<String, Object> line(long productId, int qty, String cost, String discount, String tax) {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", productId);
        line.put("quantity", qty);
        line.put("unitCost", cost);
        line.put("discountPercent", discount);
        line.put("taxPercent", tax);
        return line;
    }

    private long product(String title) throws Exception {
        return create("/api/products", Map.of("title", title, "purchasePrice", 10, "sellingPrice", 20));
    }

    private long create(String path, Map<String, Object> body) throws Exception {
        return read(mvc.perform(withJson(post(path), body).session(admin)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }

    private void deactivateProduct(long id) throws Exception {
        JsonNode p = read(mvc.perform(get("/api/products/" + id).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/products/" + id), Map.of("title", p.get("title").asText(), "purchasePrice", 10,
                "sellingPrice", 20, "active", false, "version", p.get("version").asLong())).session(admin))
                .andExpect(status().isOk());
    }

    private int stock(long productId) {
        entityManager.flush();
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }

    private static int sequenceOf(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('-') + 1));
    }
}
