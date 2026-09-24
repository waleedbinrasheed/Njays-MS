package com.inventory.sale;

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

class SaleIT extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ZoneId businessZone;

    private MockHttpSession admin;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(testUsers.admin().getUsername());
        today = LocalDate.now(businessZone);
    }

    @Test
    void saleReducesStockRecordsLedgerAndDefaultsToWalkInCustomer() throws Exception {
        long a = stockedProduct("Sold A", 10);
        long b = stockedProduct("Sold B", 5);

        JsonNode sale = read(createSale(null, List.of(line(a, 3, "500", "10", "0"), line(b, 2, "200", null, "5")), "20")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber", matchesPattern("INV-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.customer.name").value("Walk-in Customer"))
                .andExpect(jsonPath("$.subtotal").value(1900.00))      // 1500 + 400
                .andExpect(jsonPath("$.discountTotal").value(170.00))  // 150 line + 20 invoice
                .andExpect(jsonPath("$.taxTotal").value(20.00))        // 5% of 400
                .andExpect(jsonPath("$.total").value(1750.00))
                .andReturn());

        assertThat(stock(a)).isEqualTo(7);
        assertThat(stock(b)).isEqualTo(3);
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(a)).param("type", "SALE").session(admin))
                .andExpect(jsonPath("$.content[0].quantity").value(-3))
                .andExpect(jsonPath("$.content[0].previousQuantity").value(10))
                .andExpect(jsonPath("$.content[0].resultingQuantity").value(7))
                .andExpect(jsonPath("$.content[0].referenceType").value("SALE"))
                .andExpect(jsonPath("$.content[0].referenceId").value(sale.get("id").asLong()));
    }

    /** The all-or-nothing rollback is proven with real commits in SaleConcurrencyIT. */
    @Test
    void saleExceedingStockIsRejectedWithClearMessage() throws Exception {
        long a = stockedProduct("Plenty", 10);
        long b = stockedProduct("Scarce", 2);

        createSale(null, List.of(line(a, 1, "100", null, null), line(b, 3, "100", null, null)), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Scarce': available 2, requested 3"));
    }

    @Test
    void chosenCustomerMustBeActive() throws Exception {
        long a = stockedProduct("For customers", 5);
        long customerId = create("/api/customers", Map.of("name", TestUsers.uniqueName("Cust")));

        createSale(customerId, List.of(line(a, 1, "100", null, null)), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.id").value(customerId));

        JsonNode c = read(mvc.perform(get("/api/customers/" + customerId).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/customers/" + customerId), Map.of("name", c.get("name").asText(), "active", false,
                "version", c.get("version").asLong())).session(admin)).andExpect(status().isOk());
        createSale(customerId, List.of(line(a, 1, "100", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));

        mvc.perform(delete("/api/customers/" + customerId).with(csrf()).session(admin)).andExpect(status().isConflict());
    }

    @Test
    void validationRules() throws Exception {
        long a = stockedProduct("Validated", 5);
        createSale(null, List.of(), null).andExpect(status().isBadRequest());
        createSale(null, List.of(line(a, 1, "0", null, null)), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].unitPrice"));
        createSale(null, List.of(line(a, 1, "10", null, null), line(a, 1, "10", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than one line")));
        createSale(null, List.of(line(a, 1, "10", null, null)), "10.01")
                .andExpect(status().isBadRequest());

        Map<String, Object> future = sale(null, List.of(line(a, 1, "10", null, null)), null);
        future.put("saleDate", today.plusDays(1).toString());
        mvc.perform(withJson(post("/api/sales"), future).session(admin)).andExpect(status().isBadRequest());
        assertThat(stock(a)).isEqualTo(5);
    }

    @Test
    void inactiveProductCannotBeSold() throws Exception {
        long a = stockedProduct("Discontinued", 5);
        JsonNode p = read(mvc.perform(get("/api/products/" + a).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/products/" + a), Map.of("title", p.get("title").asText(), "purchasePrice", 10,
                "sellingPrice", 20, "active", false, "version", p.get("version").asLong())).session(admin))
                .andExpect(status().isOk());

        createSale(null, List.of(line(a, 1, "20", null, null)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("inactive and cannot be sold")));
    }

    @Test
    void cancelReturnsStock() throws Exception {
        long a = stockedProduct("Returned on cancel", 4);
        long id = read(createSale(null, List.of(line(a, 4, "50", null, null)), null).andReturn()).get("id").asLong();
        assertThat(stock(a)).isZero();

        mvc.perform(withJson(post("/api/sales/" + id + "/cancel"), Map.of("reason", "Customer changed mind")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stock(a)).isEqualTo(4);
        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(a)).param("type", "SALE_CANCEL")
                        .session(admin))
                .andExpect(jsonPath("$.content[0].quantity").value(4));

        mvc.perform(withJson(post("/api/sales/" + id + "/cancel"), Map.of("reason", "Again")).session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void paymentStatusAndHistoryFilters() throws Exception {
        long a = stockedProduct("Filtered sale", 10);
        JsonNode s = read(createSale(null, List.of(line(a, 1, "50", null, null)), null).andReturn());
        long id = s.get("id").asLong();

        mvc.perform(withJson(put("/api/sales/" + id + "/payment-status"), Map.of("paymentStatus", "PAID")).session(admin))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mvc.perform(get("/api/sales").param("productId", String.valueOf(a)).param("paymentStatus", "PAID").session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].createdBy.name").exists());
        mvc.perform(get("/api/sales").param("search", s.get("invoiceNumber").asText()).session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/sales").param("productId", String.valueOf(a)).param("to", today.minusDays(1).toString())
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void normalUserCanSellAndViewButNotCancel() throws Exception {
        long a = stockedProduct("Counter", 3);
        MockHttpSession cashier = login(testUsers.normalUser().getUsername());

        long id = read(mvc.perform(withJson(post("/api/sales"), sale(null, List.of(line(a, 1, "20", null, null)), null))
                        .session(cashier))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(get("/api/sales/" + id).session(cashier)).andExpect(status().isOk());
        mvc.perform(get("/api/customers/walk-in").session(cashier)).andExpect(status().isOk());
        mvc.perform(get("/api/inventory").param("search", "Counter").session(cashier)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/sales/" + id + "/cancel"), Map.of("reason", "x")).session(cashier))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private ResultActions createSale(Long customerId, List<Map<String, Object>> lines, String invoiceDiscount) throws Exception {
        return mvc.perform(withJson(post("/api/sales"), sale(customerId, lines, invoiceDiscount)).session(admin));
    }

    private Map<String, Object> sale(Long customerId, List<Map<String, Object>> lines, String invoiceDiscount) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("saleDate", today.toString());
        body.put("paymentStatus", "UNPAID");
        body.put("invoiceDiscount", invoiceDiscount);
        body.put("items", new ArrayList<>(lines));
        return body;
    }

    private Map<String, Object> line(long productId, int qty, String price, String discount, String tax) {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", productId);
        line.put("quantity", qty);
        line.put("unitPrice", price);
        line.put("discountPercent", discount);
        line.put("taxPercent", tax);
        return line;
    }

    private long stockedProduct(String title, int qty) throws Exception {
        long id = create("/api/products", Map.of("title", title, "purchasePrice", 10, "sellingPrice", 20));
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", id, "adjustmentQuantity", qty,
                "expectedCurrentQuantity", 0, "reason", "FOUND")).session(admin)).andExpect(status().isCreated());
        return id;
    }

    private long create(String path, Map<String, Object> body) throws Exception {
        return read(mvc.perform(withJson(post(path), body).session(admin)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }

    private int stock(long productId) {
        entityManager.flush();
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }
}
