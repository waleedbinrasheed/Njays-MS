package com.inventory.sample;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SampleIT extends ApiTestBase {

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
    void issueTakesStockAndPartialReturnsPutItBackAtTheOriginalPrice() throws Exception {
        long rep = representative("Ali");
        long book = book("Sample Book A", "200", "350", 60);

        JsonNode sample = read(issue(rep, today, line(book, 50, "200"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sampleNumber", matchesPattern("SMP-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.representative.id").value(rep))
                .andExpect(jsonPath("$.total").value(10000.00))
                .andExpect(jsonPath("$.totalQuantity").value(50))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.outstandingTotal").value(10000.00))
                .andReturn());
        assertThat(stock(book)).isEqualTo(10);
        long sampleId = sample.get("id").asLong();
        long itemId = sample.get("items").get(0).get("id").asLong();

        // Later price changes never touch the sample or its returns.
        rawUpdate("update product set purchase_price = 250, selling_price = 400 where id = ?", book);

        sampleReturn(sampleId, itemId, 25)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.returnNumber", matchesPattern("SMR-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.sampleNumber").value(sample.get("sampleNumber").asText()))
                .andExpect(jsonPath("$.items[0].unitPrice").value(200.00))
                .andExpect(jsonPath("$.total").value(5000.00));
        assertThat(stock(book)).isEqualTo(35);

        mvc.perform(get("/api/samples/" + sampleId).session(admin))
                .andExpect(jsonPath("$.status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.returnedQuantity").value(25))
                .andExpect(jsonPath("$.returnedTotal").value(5000.00))
                .andExpect(jsonPath("$.outstandingQuantity").value(25))
                .andExpect(jsonPath("$.outstandingTotal").value(5000.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(200.00))
                .andExpect(jsonPath("$.items[0].outstandingQuantity").value(25))
                .andExpect(jsonPath("$.returns.length()").value(1));

        sampleReturn(sampleId, itemId, 26)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("only 25 of 50 still out")));

        sampleReturn(sampleId, itemId, 25).andExpect(status().isCreated());
        assertThat(stock(book)).isEqualTo(60);
        mvc.perform(get("/api/samples/" + sampleId).session(admin))
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.outstandingQuantity").value(0))
                .andExpect(jsonPath("$.outstandingTotal").value(0.00));
        sampleReturn(sampleId, itemId, 1).andExpect(status().isBadRequest());

        mvc.perform(get("/api/stock-transactions").param("productId", String.valueOf(book)).param("type", "SAMPLE_ISSUE")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].quantity").value(-50))
                .andExpect(jsonPath("$.content[0].referenceType").value("SAMPLE_ISSUE"));
        mvc.perform(get("/api/reports/stock-movement").param("productId", String.valueOf(book)).param("type", "SAMPLE_RETURN")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reference", matchesPattern("SMR-\\d{4}-\\d{6}")));
        mvc.perform(get("/api/sample-returns").param("sampleId", String.valueOf(sampleId)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].representative.id").value(rep));
        mvc.perform(get("/api/samples").param("representativeId", String.valueOf(rep)).param("status", "RETURNED")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void severalBooksPerSample() throws Exception {
        long rep = representative("Multi");
        long a = book("Multi A", "100", "150", 10);
        long b = book("Multi B", "40", "60", 10);

        issue(rep, today, line(a, 3, "100"), line(b, 5, "55.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(8))
                .andExpect(jsonPath("$.total").value(577.50));
        assertThat(stock(a)).isEqualTo(7);
        assertThat(stock(b)).isEqualTo(5);
    }

    @Test
    void issueValidation() throws Exception {
        long rep = representative("Strict");
        long book = book("Priced", "200", "350", 5);

        issue(rep, today, line(book, 1, "350.01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be above its selling price")));
        issue(counterRepresentativeId(), today, line(book, 1, "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be issued to 'Counter Sales'")));
        issue(rep, today.plusDays(1), line(book, 1, "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Issue date cannot be in the future"));
        issue(rep, today, line(book, 1, "200"), line(book, 1, "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than one line")));
        issue(rep, today, line(book, 1, "-1")).andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/samples"), Map.of("representativeId", rep, "issueDate", today.toString(),
                        "items", List.of())).session(admin))
                .andExpect(status().isBadRequest());

        // Free samples (price 0) are allowed; the default in the screen is the purchase price.
        issue(rep, today, line(book, 1, "0")).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(0));

        issue(rep, today, line(book, 5, "200"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Priced': available 4, requested 5"));
    }

    @Test
    void inactiveRepresentativeGetsNoNewSamplesButCanStillReturn() throws Exception {
        long rep = representative("Leaving");
        long book = book("Kept", "10", "20", 5);
        JsonNode sample = read(issue(rep, today, line(book, 3, "10")).andExpect(status().isCreated()).andReturn());

        rawUpdate("update representative set active = false where id = ?", rep);
        issue(rep, today, line(book, 1, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));
        sampleReturn(sample.get("id").asLong(), sample.get("items").get(0).get("id").asLong(), 3)
                .andExpect(status().isCreated());
        assertThat(stock(book)).isEqualTo(5);
    }

    @Test
    void cancelPutsEverythingBackAndOnlyWhileNothingIsReturned() throws Exception {
        long rep = representative("Cancel");
        long book = book("Cancellable", "10", "20", 10);
        JsonNode sample = read(issue(rep, today, line(book, 4, "10")).andReturn());
        long sampleId = sample.get("id").asLong();

        cancel(sampleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Wrong representative"))
                .andExpect(jsonPath("$.outstandingQuantity").value(0));
        assertThat(stock(book)).isEqualTo(10);
        cancel(sampleId).andExpect(status().isConflict());
        sampleReturn(sampleId, sample.get("items").get(0).get("id").asLong(), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("is cancelled")));

        JsonNode other = read(issue(rep, today, line(book, 4, "10")).andReturn());
        sampleReturn(other.get("id").asLong(), other.get("items").get(0).get("id").asLong(), 1).andExpect(status().isCreated());
        cancel(other.get("id").asLong())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("has returns")));
        mvc.perform(withJson(post("/api/samples/" + sampleId + "/cancel"), Map.of("reason", " ")).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnValidation() throws Exception {
        long rep = representative("Returns");
        long book = book("Returned", "10", "20", 10);
        JsonNode sample = read(issue(rep, today.minusDays(2), line(book, 4, "10")).andReturn());
        long sampleId = sample.get("id").asLong();
        long itemId = sample.get("items").get(0).get("id").asLong();

        mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.minusDays(3).toString(),
                        "items", List.of(Map.of("sampleItemId", itemId, "quantity", 1)))).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Return date cannot be before the issue date"));
        mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.plusDays(1).toString(),
                        "items", List.of(Map.of("sampleItemId", itemId, "quantity", 1)))).session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.toString(),
                        "items", List.of(Map.of("sampleItemId", itemId, "quantity", 1), Map.of("sampleItemId", itemId, "quantity", 1))))
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("more than once")));
        mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.toString(),
                        "items", List.of(Map.of("sampleItemId", -1, "quantity", 1)))).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is not part of")));
        sampleReturn(sampleId, itemId, 0).andExpect(status().isBadRequest());
    }

    @Test
    void viewersCanLookButNotIssueReturnOrCancel() throws Exception {
        long rep = representative("Perm");
        long book = book("Guarded sample", "10", "20", 5);
        JsonNode sample = read(issue(rep, today, line(book, 2, "10")).andReturn());
        long sampleId = sample.get("id").asLong();
        MockHttpSession viewer = login(testUsers.normalUser().getUsername());

        mvc.perform(get("/api/samples").session(viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/samples/" + sampleId).session(viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/sample-returns").session(viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/sample-reports/representatives").session(viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/sample-reports/lines").session(viewer)).andExpect(status().isOk());

        mvc.perform(withJson(post("/api/samples"), Map.of("representativeId", rep, "issueDate", today.toString(),
                "items", List.of(line(book, 1, "10")))).session(viewer)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/samples/" + sampleId + "/cancel"), Map.of("reason", "x")).session(viewer))
                .andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.toString(),
                        "items", List.of(Map.of("sampleItemId", sample.get("items").get(0).get("id").asLong(), "quantity", 1))))
                        .session(viewer))
                .andExpect(status().isForbidden());
        assertThat(stock(book)).isEqualTo(3);

        mvc.perform(get("/api/samples")).andExpect(status().isUnauthorized());
    }

    @Test
    void samplesNeverCountAsSales() throws Exception {
        long rep = representative("NotASale");
        long book = book("Not sold", "100", "150", 10);
        JsonNode before = read(mvc.perform(get("/api/dashboard").session(admin)).andReturn());

        issue(rep, today, line(book, 5, "100")).andExpect(status().isCreated());

        JsonNode after = read(mvc.perform(get("/api/dashboard").session(admin)).andReturn());
        assertThat(after.get("todaySales").get("count").asLong()).isEqualTo(before.get("todaySales").get("count").asLong());
        assertThat(after.get("todaySales").get("total").decimalValue())
                .isEqualByComparingTo(before.get("todaySales").get("total").decimalValue());
        mvc.perform(get("/api/sales").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/reports/sales").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/reports/representatives").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.content[0].invoices").value(0))
                .andExpect(jsonPath("$.content[0].salesTotal").value(0));
    }

    @Test
    void representativeWithSamplesCannotBeDeleted() throws Exception {
        long rep = representative("Holder");
        long book = book("Held", "10", "20", 5);
        issue(rep, today, line(book, 1, "10")).andExpect(status().isCreated());
        mvc.perform(delete("/api/representatives/" + rep).with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()).session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void sampleReportPerRepresentativeAndPerBook() throws Exception {
        long ali = representative("Report Ali");
        long sara = representative("Report Sara");
        long a = book("Report A", "200", "350", 100);
        long b = book("Report B", "50", "80", 100);

        JsonNode s1 = read(issue(ali, today, line(a, 50, "200"), line(b, 10, "50")).andReturn());
        sampleReturn(s1.get("id").asLong(), s1.get("items").get(0).get("id").asLong(), 25).andExpect(status().isCreated());
        issue(sara, today, line(a, 5, "180")).andExpect(status().isCreated());
        JsonNode cancelled = read(issue(ali, today, line(b, 7, "50")).andReturn());
        cancel(cancelled.get("id").asLong()).andExpect(status().isOk());
        flush();

        String reps = ali + "";
        mvc.perform(get("/api/sample-reports/representatives").param("representativeId", reps).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(nameOf(ali)))
                .andExpect(jsonPath("$.content[0].samples").value(1)) // the cancelled one is not counted
                .andExpect(jsonPath("$.content[0].issuedQuantity").value(60))
                .andExpect(jsonPath("$.content[0].issuedAmount").value(10500.00))
                .andExpect(jsonPath("$.content[0].returnedQuantity").value(25))
                .andExpect(jsonPath("$.content[0].returnedAmount").value(5000.00))
                .andExpect(jsonPath("$.content[0].outstandingQuantity").value(35))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(5500.00))
                .andExpect(jsonPath("$.content[0].lastIssue").value(today.toString()));

        // Book filter: only Book A lines, across both representatives.
        mvc.perform(get("/api/sample-reports/representatives").param("productId", String.valueOf(a))
                        .param("sort", "name,asc").session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary.issuedQuantity").value(55))
                .andExpect(jsonPath("$.summary.issuedAmount").value(10900.00))
                .andExpect(jsonPath("$.summary.outstandingAmount").value(5900.00));

        // Detail rows: the cancelled sample is listed but left out of the totals.
        mvc.perform(get("/api/sample-reports/lines").param("representativeId", reps).param("sort", "issuedQuantity,desc")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].issuedQuantity").value(50))
                .andExpect(jsonPath("$.content[0].returnedQuantity").value(25))
                .andExpect(jsonPath("$.content[0].outstandingAmount").value(5000.00))
                .andExpect(jsonPath("$.content[0].status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.summary.samples").value(1))
                .andExpect(jsonPath("$.summary.issuedQuantity").value(60))
                .andExpect(jsonPath("$.summary.outstandingQuantity").value(35));
        mvc.perform(get("/api/sample-reports/lines").param("representativeId", reps).param("status", "CANCELLED")
                        .session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].outstandingQuantity").value(0))
                .andExpect(jsonPath("$.summary.issuedQuantity").value(0));
        mvc.perform(get("/api/sample-reports/lines").param("from", today.plusDays(1).toString())
                        .param("to", today.toString()).session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sample-reports/lines").param("sort", "title;drop").session(admin))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> line(long productId, int qty, String price) {
        return Map.of("productId", productId, "quantity", qty, "unitPrice", price);
    }

    @SafeVarargs
    private ResultActions issue(long rep, LocalDate date, Map<String, Object>... lines) throws Exception {
        return mvc.perform(withJson(post("/api/samples"), Map.of("representativeId", rep, "issueDate", date.toString(),
                "items", List.of(lines))).session(admin));
    }

    private ResultActions sampleReturn(long sampleId, long itemId, int qty) throws Exception {
        return mvc.perform(withJson(post("/api/sample-returns"), Map.of("sampleId", sampleId, "returnDate", today.toString(),
                "items", List.of(Map.of("sampleItemId", itemId, "quantity", qty)))).session(admin));
    }

    private ResultActions cancel(long sampleId) throws Exception {
        return mvc.perform(withJson(post("/api/samples/" + sampleId + "/cancel"), Map.of("reason", "Wrong representative"))
                .session(admin));
    }

    private long representative(String prefix) throws Exception {
        return create("/api/representatives", Map.of("name", TestUsers.uniqueName(prefix), "area", "Lahore"));
    }

    private String nameOf(long rep) {
        return jdbc.queryForObject("select name from representative where id = ?", String.class, rep);
    }

    private long book(String title, String purchasePrice, String sellingPrice, int qty) throws Exception {
        long id = create("/api/products", Map.of("title", title, "purchasePrice", new BigDecimal(purchasePrice),
                "sellingPrice", new BigDecimal(sellingPrice)));
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", id, "adjustmentQuantity", qty,
                "expectedCurrentQuantity", 0, "reason", "FOUND")).session(admin)).andExpect(status().isCreated());
        return id;
    }

    private long create(String path, Map<String, Object> body) throws Exception {
        return read(mvc.perform(withJson(post(path), body).session(admin)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }

    private void flush() {
        entityManager.flush();
    }

    /** Direct change outside JPA; the persistence context is cleared so later requests see it. */
    private void rawUpdate(String sql, Object... args) {
        entityManager.flush();
        jdbc.update(sql, args);
        entityManager.clear();
    }

    private int stock(long productId) {
        entityManager.flush();
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }
}
