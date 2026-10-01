package com.inventory.returns;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReturnIT extends ApiTestBase {

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

    // --- sales returns ---

    @Test
    void partialSalesReturnsAddStockAndCannotExceedWhatWasSold() throws Exception {
        long product = stockedProduct("Returnable", 10);
        JsonNode sale = sell(product, 5, "100");
        long itemId = sale.get("items").get(0).get("id").asLong();
        long saleId = sale.get("id").asLong();

        saleReturn(saleId, itemId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnNumber", matchesPattern("SRT-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.documentNumber").value(sale.get("invoiceNumber").asText()))
                .andExpect(jsonPath("$.party.name").value("Counter Sales"))
                .andExpect(jsonPath("$.total").value(200.00));
        assertThat(stock(product)).isEqualTo(7);

        mvc.perform(get("/api/sale-returns/returnable/" + saleId).session(admin))
                .andExpect(jsonPath("$[0].originalQuantity").value(5))
                .andExpect(jsonPath("$[0].returnedQuantity").value(2))
                .andExpect(jsonPath("$[0].returnableQuantity").value(3));

        saleReturn(saleId, itemId, 4)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot return 4 of 'Returnable': only 3 of 5 left to return"));

        saleReturn(saleId, itemId, 3).andExpect(status().isCreated());
        assertThat(stock(product)).isEqualTo(10);
        saleReturn(saleId, itemId, 1).andExpect(status().isBadRequest());

        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(product)).param("type", "SALE_RETURN")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].referenceType").value("SALE_RETURN"));
        mvc.perform(get("/api/sale-returns").param("saleId", String.valueOf(saleId)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void refundsAddUpExactlyToTheLineTotal() throws Exception {
        long product = stockedProduct("Thirds", 3);
        JsonNode sale = sell(product, 3, "33.34"); // line total 100.02
        long saleId = sale.get("id").asLong();
        long itemId = sale.get("items").get(0).get("id").asLong();

        double first = read(saleReturn(saleId, itemId, 1).andReturn()).get("total").asDouble();
        double second = read(saleReturn(saleId, itemId, 1).andReturn()).get("total").asDouble();
        double last = read(saleReturn(saleId, itemId, 1).andReturn()).get("total").asDouble();
        assertThat(first + second + last).isEqualTo(100.02, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void invoiceWithReturnsCannotBeCancelledAndCancelledInvoiceCannotBeReturned() throws Exception {
        long product = stockedProduct("Guarded", 5);
        JsonNode sale = sell(product, 2, "50");
        long saleId = sale.get("id").asLong();
        saleReturn(saleId, sale.get("items").get(0).get("id").asLong(), 1).andExpect(status().isCreated());

        mvc.perform(withJson(post("/api/sales/" + saleId + "/cancel"), Map.of("reason", "x")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("has returns")));

        JsonNode other = sell(product, 1, "50");
        mvc.perform(withJson(post("/api/sales/" + other.get("id").asLong() + "/cancel"), Map.of("reason", "x")).session(admin))
                .andExpect(status().isOk());
        saleReturn(other.get("id").asLong(), other.get("items").get(0).get("id").asLong(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is cancelled")));
    }

    @Test
    void returnValidation() throws Exception {
        long product = stockedProduct("Checked", 5);
        JsonNode a = sell(product, 2, "10");
        JsonNode b = sell(product, 2, "10");
        long aId = a.get("id").asLong();
        long aItem = a.get("items").get(0).get("id").asLong();
        long bItem = b.get("items").get(0).get("id").asLong();

        saleReturn(aId, bItem, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is not part of")));

        Map<String, Object> dup = returnBody(aId, List.of(line(aItem, 1), line(aItem, 1)));
        mvc.perform(withJson(post("/api/sale-returns"), dup).session(admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than once")));

        Map<String, Object> early = returnBody(aId, List.of(line(aItem, 1)));
        early.put("returnDate", today.minusDays(1).toString());
        mvc.perform(withJson(post("/api/sale-returns"), early).session(admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Return date cannot be before the sale date"));

        Map<String, Object> noReason = returnBody(aId, List.of(line(aItem, 1)));
        noReason.put("reason", "");
        mvc.perform(withJson(post("/api/sale-returns"), noReason).session(admin)).andExpect(status().isBadRequest());
    }

    // --- purchase returns ---

    @Test
    void purchaseReturnRemovesStockAndIsRefusedWhenStockIsGone() throws Exception {
        long supplier = create("/api/suppliers", Map.of("name", TestUsers.uniqueName("RetSupplier")));
        long product = create("/api/products", Map.of("title", "Sent back", "purchasePrice", 10, "sellingPrice", 20));
        JsonNode purchase = read(mvc.perform(withJson(post("/api/purchases"), Map.of("supplierId", supplier,
                        "purchaseDate", today.toString(), "paymentStatus", "UNPAID",
                        "items", List.of(Map.of("productId", product, "quantity", 6, "unitCost", "10")))).session(admin))
                .andExpect(status().isCreated()).andReturn());
        long purchaseId = purchase.get("id").asLong();
        long itemId = purchase.get("items").get(0).get("id").asLong();

        mvc.perform(withJson(post("/api/purchase-returns"), returnBody(purchaseId, List.of(line(itemId, 2)))).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnNumber", matchesPattern("PRT-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.total").value(20.00));
        assertThat(stock(product)).isEqualTo(4);

        sell(product, 3, "20"); // stock now 1, but 4 are still returnable on paper
        mvc.perform(withJson(post("/api/purchase-returns"), returnBody(purchaseId, List.of(line(itemId, 4)))).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Sent back': available 1, requested 4"));

        mvc.perform(withJson(post("/api/purchases/" + purchaseId + "/cancel"), Map.of("reason", "x")).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("has returns")));
    }

    @Test
    void permissions() throws Exception {
        long product = stockedProduct("Perm return", 3);
        JsonNode sale = sell(product, 1, "10");
        MockHttpSession cashier = login(testUsers.normalUser().getUsername());

        mvc.perform(withJson(post("/api/sale-returns"),
                        returnBody(sale.get("id").asLong(), List.of(line(sale.get("items").get(0).get("id").asLong(), 1))))
                        .session(cashier))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sale-returns").session(cashier)).andExpect(status().isOk());
        mvc.perform(get("/api/purchase-returns").session(cashier)).andExpect(status().isOk());
    }

    // --- helpers ---

    private ResultActions saleReturn(long saleId, long itemId, int qty) throws Exception {
        return mvc.perform(withJson(post("/api/sale-returns"), returnBody(saleId, List.of(line(itemId, qty)))).session(admin));
    }

    private Map<String, Object> returnBody(long documentId, List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("documentId", documentId);
        body.put("returnDate", today.toString());
        body.put("reason", "Damaged cover");
        body.put("items", lines);
        return body;
    }

    private Map<String, Object> line(long itemId, int qty) {
        return Map.of("documentItemId", itemId, "quantity", qty);
    }

    private JsonNode sell(long productId, int qty, String price) throws Exception {
        return read(mvc.perform(withJson(post("/api/sales"), Map.of("representativeId", counterRepresentativeId(),
                        "saleDate", today.toString(), "paymentStatus", "PAID",
                        "items", List.of(Map.of("productId", productId, "quantity", qty, "unitPrice", price)))).session(admin))
                .andExpect(status().isCreated()).andReturn());
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
