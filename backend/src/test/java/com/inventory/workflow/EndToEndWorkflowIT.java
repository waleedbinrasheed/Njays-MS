package com.inventory.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One book through every kind of stock movement, the way the shop uses the system: set up master
 * data, buy, sell to a representative's customer, take a return, issue and take back samples, send
 * some back to the supplier, adjust after a count. Stock, the ledger, the movement report and the
 * reports must all agree at the end.
 */
class EndToEndWorkflowIT extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ZoneId businessZone;

    private MockHttpSession admin;
    private String today;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(testUsers.admin().getUsername());
        today = LocalDate.now(businessZone).toString();
    }

    @Test
    void bookThroughEveryMovementKeepsStockLedgerAndReportsInAgreement() throws Exception {
        // Master data.
        long supplier = create("/api/suppliers", Map.of("name", TestUsers.uniqueName("Flow supplier")));
        long rep = create("/api/representatives", Map.of("name", TestUsers.uniqueName("Flow rep"), "area", "Lahore"));
        long school = create("/api/customers", Map.of("name", TestUsers.uniqueName("Flow school"), "representativeId", rep,
                "phone", "042-1112223"));
        long book = create("/api/products", Map.of("title", TestUsers.uniqueName("Flow book"), "purchasePrice", 200,
                "sellingPrice", 350));

        // Purchase 10 (stock 10).
        JsonNode purchase = created("/api/purchases", Map.of("supplierId", supplier, "purchaseDate", today,
                "paymentStatus", "PAID", "items", List.of(Map.of("productId", book, "quantity", 10, "unitCost", "200"))));
        // Sell 4 to the representative for the school (stock 6).
        JsonNode sale = created("/api/sales", Map.of("representativeId", rep, "customerId", school, "saleDate", today,
                "paymentStatus", "UNPAID", "items", List.of(Map.of("productId", book, "quantity", 4, "unitPrice", "350"))));
        // One copy comes back (stock 7).
        created("/api/sale-returns", Map.of("documentId", sale.get("id").asLong(), "returnDate", today, "reason", "Damaged",
                "items", List.of(Map.of("documentItemId", sale.get("items").get(0).get("id").asLong(), "quantity", 1))));
        // 3 samples to the representative for the school (stock 4), 2 come back (stock 6).
        JsonNode sample = created("/api/samples", Map.of("representativeId", rep, "customerId", school, "issueDate", today,
                "items", List.of(Map.of("productId", book, "quantity", 3, "unitPrice", "200"))));
        created("/api/sample-returns", Map.of("sampleId", sample.get("id").asLong(), "returnDate", today,
                "items", List.of(Map.of("sampleItemId", sample.get("items").get(0).get("id").asLong(), "quantity", 2))));
        // 1 sent back to the supplier (stock 5).
        created("/api/purchase-returns", Map.of("documentId", purchase.get("id").asLong(), "returnDate", today,
                "reason", "Misprint", "items", List.of(Map.of("documentItemId", purchase.get("items").get(0).get("id").asLong(),
                        "quantity", 1))));
        // A count finds one missing (stock 4).
        created("/api/stock-adjustments", Map.of("productId", book, "adjustmentQuantity", -1, "expectedCurrentQuantity", 5,
                "reason", "LOST"));

        // Stock and ledger agree.
        entityManager.flush();
        assertThat(jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, book)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(quantity) from stock_transaction where product_id = ?", Integer.class, book))
                .isEqualTo(4);
        mvc.perform(get("/api/reports/stock-movement").param("productId", String.valueOf(book)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(7))
                .andExpect(jsonPath("$.summary.unitsIn").value(13))   // 10 + 1 + 2
                .andExpect(jsonPath("$.summary.unitsOut").value(9));  // 4 + 3 + 1 + 1

        // Sales figures: 4 x 350 sold, 1 x 350 returned; samples are not sales.
        mvc.perform(get("/api/reports/representatives").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.content[0].invoices").value(1))
                .andExpect(jsonPath("$.content[0].salesTotal").value(1400.00))
                .andExpect(jsonPath("$.content[0].returns").value(350.00))
                .andExpect(jsonPath("$.content[0].netSales").value(1050.00))
                .andExpect(jsonPath("$.content[0].owed").value(1400.00));
        mvc.perform(get("/api/sample-reports/representatives").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.content[0].issuedAmount").value(600.00))
                .andExpect(jsonPath("$.content[0].returnedAmount").value(400.00))
                .andExpect(jsonPath("$.content[0].outstandingQuantity").value(1))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(200.00));
        mvc.perform(get("/api/reports/inventory").param("productId", String.valueOf(book)).session(admin))
                .andExpect(jsonPath("$.content[0].quantity").value(4))
                .andExpect(jsonPath("$.content[0].stockValue").value(800.00));
    }

    @Test
    void everyProductsStockEqualsItsLedger() {
        // Across the whole test database, including data committed by the concurrency tests.
        List<Map<String, Object>> mismatches = jdbc.queryForList("""
                select i.product_id, i.quantity, coalesce(sum(t.quantity), 0) as ledger
                from inventory i left join stock_transaction t on t.product_id = i.product_id
                group by i.product_id, i.quantity
                having i.quantity <> coalesce(sum(t.quantity), 0)""");
        assertThat(mismatches).isEmpty();
    }

    private long create(String path, Map<String, Object> body) throws Exception {
        return created(path, body).get("id").asLong();
    }

    private JsonNode created(String path, Map<String, Object> body) throws Exception {
        return read(mvc.perform(withJson(post(path), body).session(admin)).andExpect(status().isCreated()).andReturn());
    }
}
