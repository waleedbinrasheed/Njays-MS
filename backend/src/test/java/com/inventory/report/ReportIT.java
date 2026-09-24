package com.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.ApiTestBase;
import com.inventory.support.TestUsers;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIT extends ApiTestBase {

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
    void inventoryReportFiltersAndTotalsStockValue() throws Exception {
        long category = create("/api/categories", Map.of("name", TestUsers.uniqueName("RptCat")));
        long a = product("Rpt A", category, "100.00");
        long b = product("Rpt B", category, "50.00");
        product("Rpt C", category, "10.00"); // zero stock
        adjust(a, 4);
        adjust(b, 2);

        report("/api/reports/inventory").param("categoryId", String.valueOf(category)).param("sort", "stockValue,desc")
                .perform()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Rpt A"))
                .andExpect(jsonPath("$.content[0].stockValue").value(400.00))
                .andExpect(jsonPath("$.summary.units").value(6))
                .andExpect(jsonPath("$.summary.stockValue").value(500.00));

        report("/api/reports/inventory").param("categoryId", String.valueOf(category)).param("stock", "OUT").perform()
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Rpt C"));
        report("/api/reports/inventory").param("sort", "passwordHash").perform().andExpect(status().isBadRequest());
    }

    @Test
    void lowStockReportShowsShortfall() throws Exception {
        long category = create("/api/categories", Map.of("name", TestUsers.uniqueName("LowCat")));
        long low = product("Low one", category, "10");
        long fine = product("Fine one", category, "10");
        adjust(low, 2);
        adjust(fine, 20);
        reorder(low, 5);
        reorder(fine, 5);

        report("/api/reports/low-stock").param("categoryId", String.valueOf(category)).perform()
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Low one"))
                .andExpect(jsonPath("$.content[0].shortfall").value(3))
                .andExpect(jsonPath("$.summary.unitsToReorder").value(3));
    }

    @Test
    void salesReportFiltersAndCountsOnlyCompletedInvoices() throws Exception {
        long customer = create("/api/customers", Map.of("name", TestUsers.uniqueName("RptCust")));
        long p = product("Sold in report", null, "10");
        adjust(p, 10);
        JsonNode s1 = sale(customer, p, 2, "100", "PAID");
        sale(customer, p, 1, "50", "UNPAID");
        JsonNode s3 = sale(customer, p, 1, "70", "PAID");
        mvc.perform(withJson(post("/api/sales/" + s3.get("id").asLong() + "/cancel"), Map.of("reason", "test")).session(admin))
                .andExpect(status().isOk());

        report("/api/reports/sales").param("customerId", String.valueOf(customer)).param("sort", "total,desc").perform()
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].number").value(s1.get("invoiceNumber").asText()))
                .andExpect(jsonPath("$.content[0].createdBy").exists())
                .andExpect(jsonPath("$.content[0].items").value(1))
                .andExpect(jsonPath("$.summary.documents").value(2))
                .andExpect(jsonPath("$.summary.total").value(250.00))
                .andExpect(jsonPath("$.summary.paid").value(200.00))
                .andExpect(jsonPath("$.summary.unpaid").value(50.00));

        report("/api/reports/sales").param("customerId", String.valueOf(customer)).param("paymentStatus", "UNPAID").perform()
                .andExpect(jsonPath("$.totalElements").value(1));
        report("/api/reports/sales").param("customerId", String.valueOf(customer)).param("from", today.plusDays(1).toString())
                .perform().andExpect(jsonPath("$.totalElements").value(0));
        report("/api/reports/sales").param("from", today.toString()).param("to", today.minusDays(1).toString()).perform()
                .andExpect(status().isBadRequest());
    }

    @Test
    void purchaseReportByProductAndSupplier() throws Exception {
        long supplier = create("/api/suppliers", Map.of("name", TestUsers.uniqueName("RptSup")));
        long p = product("Bought in report", null, "10");
        long other = product("Other bought", null, "10");
        purchase(supplier, p, 3, "20");
        purchase(supplier, other, 1, "30");

        report("/api/reports/purchases").param("supplierId", String.valueOf(supplier)).perform()
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary.total").value(90.00));
        report("/api/reports/purchases").param("supplierId", String.valueOf(supplier)).param("productId", String.valueOf(p))
                .perform()
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].total").value(60.00));
    }

    @Test
    void stockMovementReportShowsDocumentReferences() throws Exception {
        long p = product("Moved in report", null, "10");
        adjust(p, 5);
        JsonNode s = sale(null, p, 2, "10", "PAID");

        report("/api/reports/stock-movement").param("productId", String.valueOf(p)).perform()
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("SALE"))
                .andExpect(jsonPath("$.content[0].reference").value(s.get("invoiceNumber").asText()))
                .andExpect(jsonPath("$.content[0].previousQuantity").value(5))
                .andExpect(jsonPath("$.content[0].resultingQuantity").value(3))
                .andExpect(jsonPath("$.content[1].reference", containsString("ADJ-")))
                .andExpect(jsonPath("$.summary.unitsIn").value(5))
                .andExpect(jsonPath("$.summary.unitsOut").value(2));
        report("/api/reports/stock-movement").param("productId", String.valueOf(p)).param("type", "ADJUSTMENT").perform()
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void dashboardReflectsTodaysActivity() throws Exception {
        JsonNode before = read(dashboardAs(admin));
        long p = product("Dashboard book", null, "10");
        adjust(p, 3);
        sale(null, p, 1, "123.45", "PAID");

        JsonNode after = read(dashboardAs(admin));
        assertThat(after.get("today").asText()).isEqualTo(today.toString());
        assertThat(after.get("todaySales").get("count").asLong()).isEqualTo(before.get("todaySales").get("count").asLong() + 1);
        assertThat(after.get("todaySales").get("total").decimalValue())
                .isEqualByComparingTo(before.get("todaySales").get("total").decimalValue().add(new java.math.BigDecimal("123.45")));
        assertThat(after.get("monthSales").get("count").asLong()).isGreaterThanOrEqualTo(after.get("todaySales").get("count").asLong());
        assertThat(after.get("totalProducts").asLong()).isEqualTo(before.get("totalProducts").asLong() + 1);
        assertThat(after.get("recentSales").get(0).get("total").decimalValue()).isEqualByComparingTo("123.45");
        assertThat(after.get("recentMovements").get(0).get("type").asText()).isEqualTo("SALE");
    }

    @Test
    void dashboardHidesSectionsTheViewerCannotSee() throws Exception {
        // A role with only the dashboard: no stock, sales or purchase figures.
        mvc.perform(withJson(post("/api/roles"), Map.of("name", "DASH_ONLY", "permissions", List.of("DASHBOARD_VIEW")))
                .session(admin)).andExpect(status().isCreated());
        MockHttpSession viewer = login(testUsers.create("DASH_ONLY").getUsername());

        mvc.perform(get("/api/dashboard").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaySales").value(nullValue()))
                .andExpect(jsonPath("$.todayPurchases").value(nullValue()))
                .andExpect(jsonPath("$.totalProducts").value(nullValue()))
                .andExpect(jsonPath("$.recentSales").value(nullValue()));
    }

    @Test
    void reportsNeedReportPermission() throws Exception {
        MockHttpSession normal = login(testUsers.normalUser().getUsername());
        for (String path : List.of("/api/reports/inventory", "/api/reports/sales", "/api/reports/purchases",
                "/api/reports/stock-movement", "/api/reports/low-stock")) {
            mvc.perform(get(path).session(normal)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/dashboard").session(normal)).andExpect(status().isOk());
    }

    // --- helpers ---

    /** Report queries use JDBC, so pending JPA changes in this shared test transaction must be flushed first. */
    private final class Report {
        private final MockHttpServletRequestBuilder builder;

        Report(String path) {
            this.builder = get(path).session(admin);
        }

        Report param(String name, String value) {
            builder.param(name, value);
            return this;
        }

        org.springframework.test.web.servlet.ResultActions perform() throws Exception {
            entityManager.flush();
            return mvc.perform(builder);
        }
    }

    private Report report(String path) {
        return new Report(path);
    }

    private org.springframework.test.web.servlet.MvcResult dashboardAs(MockHttpSession session) throws Exception {
        entityManager.flush();
        return mvc.perform(get("/api/dashboard").session(session)).andExpect(status().isOk()).andReturn();
    }

    private long product(String title, Long categoryId, String purchasePrice) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("title", title, "purchasePrice", purchasePrice, "sellingPrice", 200));
        if (categoryId != null) body.put("categoryId", categoryId);
        return create("/api/products", body);
    }

    private void adjust(long productId, int qty) throws Exception {
        entityManager.flush();
        int current = ((Number) entityManager.createNativeQuery("select quantity from inventory where product_id = ?1")
                .setParameter(1, productId).getSingleResult()).intValue();
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", productId, "adjustmentQuantity", qty,
                "expectedCurrentQuantity", current, "reason", "FOUND")).session(admin)).andExpect(status().isCreated());
    }

    private void reorder(long productId, int level) throws Exception {
        mvc.perform(withJson(put("/api/inventory/" + productId + "/reorder-level"), Map.of("reorderLevel", level)).session(admin))
                .andExpect(status().isOk());
    }

    private JsonNode sale(Long customerId, long productId, int qty, String price, String payment) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("saleDate", today.toString(), "paymentStatus", payment,
                "items", List.of(Map.of("productId", productId, "quantity", qty, "unitPrice", price))));
        if (customerId != null) body.put("customerId", customerId);
        return read(mvc.perform(withJson(post("/api/sales"), body).session(admin)).andExpect(status().isCreated()).andReturn());
    }

    private void purchase(long supplierId, long productId, int qty, String cost) throws Exception {
        mvc.perform(withJson(post("/api/purchases"), Map.of("supplierId", supplierId, "purchaseDate", today.toString(),
                        "paymentStatus", "PAID", "items", List.of(Map.of("productId", productId, "quantity", qty, "unitCost", cost))))
                        .session(admin))
                .andExpect(status().isCreated());
    }

    private long create(String path, Map<String, Object> body) throws Exception {
        return read(mvc.perform(withJson(post(path), body).session(admin)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }
}
