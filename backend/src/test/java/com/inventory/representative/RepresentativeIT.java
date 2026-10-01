package com.inventory.representative;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RepresentativeIT extends ApiTestBase {

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

    // ---------------------------------------------------------------- representatives

    @Test
    void createSearchAndRejectDuplicateNames() throws Exception {
        String name = TestUsers.uniqueName("Rep");
        mvc.perform(withJson(post("/api/representatives"), Map.of("name", name, "area", "Lahore - Gulberg",
                        "phone", "0300-1112223")).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.area").value("Lahore - Gulberg"))
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.customerCount").value(0));

        mvc.perform(withJson(post("/api/representatives"), Map.of("name", name.toUpperCase())).session(admin))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/representatives").param("search", name).param("area", "gulberg").session(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void counterSalesRepresentativeIsProtected() throws Exception {
        JsonNode counter = read(mvc.perform(get("/api/representatives/counter").session(admin))
                .andExpect(jsonPath("$.name").value("Counter Sales"))
                .andExpect(jsonPath("$.builtIn").value(true))
                .andReturn());
        long id = counter.get("id").asLong();

        mvc.perform(withJson(put("/api/representatives/" + id), Map.of("name", "Counter Sales", "active", false,
                        "version", counter.get("version").asLong())).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The Counter Sales representative cannot be deactivated"));
        mvc.perform(delete("/api/representatives/" + id).with(csrf()).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void representativeWithCustomersCanOnlyBeDeactivated() throws Exception {
        long rep = createRep("Busy");
        createCustomer("Busy customer", rep);
        mvc.perform(delete("/api/representatives/" + rep).with(csrf()).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Deactivate it instead")));

        long unused = createRep("Unused");
        mvc.perform(delete("/api/representatives/" + unused).with(csrf()).session(admin)).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- customers

    @Test
    void customersNeedAnActiveRepresentative() throws Exception {
        mvc.perform(withJson(post("/api/customers"), Map.of("name", "No rep")).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the customer's representative"));

        long inactive = createRep("Gone");
        deactivateRep(inactive);
        mvc.perform(withJson(post("/api/customers"), Map.of("name", "To inactive", "representativeId", inactive)).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));

        long rep = createRep("Active");
        mvc.perform(withJson(post("/api/customers"), Map.of("name", "Assigned", "representativeId", rep)).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.representative.id").value(rep))
                .andExpect(jsonPath("$.representative.name").exists());
    }

    @Test
    void walkInCustomerDoesNotNeedARepresentative() throws Exception {
        JsonNode walkIn = read(mvc.perform(get("/api/customers/walk-in").session(admin)).andReturn());
        mvc.perform(withJson(put("/api/customers/" + walkIn.get("id").asLong()), Map.of("name", "Walk-in Customer",
                        "phone", "042-0000000", "version", walkIn.get("version").asLong())).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representative").doesNotExist());
    }

    @Test
    void customersCanBeListedByRepresentativeOrUnassigned() throws Exception {
        long rep = createRep("Lister");
        createCustomer("Rep cust 1", rep);
        createCustomer("Rep cust 2", rep);
        long legacy = createCustomer("Legacy cust", rep);
        rawUpdate("update customer set representative_id = null where id = ?", legacy);

        mvc.perform(get("/api/customers").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/customers").param("unassigned", "true").param("search", "Legacy cust").session(admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].representative").doesNotExist());
        mvc.perform(get("/api/representatives/" + rep).session(admin)).andExpect(jsonPath("$.customerCount").value(2));
    }

    @Test
    void moveAllCustomersToAnotherRepresentative() throws Exception {
        long from = createRep("Leaving");
        long to = createRep("Taking over");
        long c1 = createCustomer("Moved 1", from);
        createCustomer("Moved 2", from);
        long versionBefore = read(mvc.perform(get("/api/customers/" + c1).session(admin)).andReturn()).get("version").asLong();

        mvc.perform(withJson(post("/api/representatives/" + from + "/move-customers"), Map.of("targetRepresentativeId", to))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(2))
                .andExpect(jsonPath("$.to.id").value(to));

        mvc.perform(get("/api/customers").param("representativeId", String.valueOf(to)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/customers/" + c1).session(admin))
                .andExpect(jsonPath("$.representative.id").value(to))
                .andExpect(jsonPath("$.version").value(versionBefore + 1));

        mvc.perform(withJson(post("/api/representatives/" + to + "/move-customers"), Map.of("targetRepresentativeId", to))
                .session(admin)).andExpect(status().isBadRequest());
        deactivateRep(from);
        mvc.perform(withJson(post("/api/representatives/" + to + "/move-customers"), Map.of("targetRepresentativeId", from))
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));
    }

    // ---------------------------------------------------------------- sales

    @Test
    void saleIsBilledToTheRepresentativeWithOptionalCustomer() throws Exception {
        long rep = createRep("Seller");
        long customer = createCustomer("Buyer of rep", rep);
        long product = stockedProduct(10);

        sale(rep, customer, product, 2, "100").andExpect(status().isCreated())
                .andExpect(jsonPath("$.representative.id").value(rep))
                .andExpect(jsonPath("$.representative.area").exists())
                .andExpect(jsonPath("$.customer.id").value(customer));
        sale(rep, null, product, 1, "100").andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer").doesNotExist());

        mvc.perform(get("/api/sales").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].representative.id").value(rep));
        String repName = jdbc.queryForObject("select name from representative where id = ?", String.class, rep);
        mvc.perform(get("/api/sales").param("search", repName).session(admin))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void saleRules() throws Exception {
        long product = stockedProduct(10);
        long rep = createRep("Rules");
        long otherRep = createRep("Other");
        long othersCustomer = createCustomer("Not yours", otherRep);

        Map<String, Object> noRep = saleBody(null, null, product, 1, "10");
        noRep.remove("representativeId");
        mvc.perform(withJson(post("/api/sales"), noRep).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("representativeId"));

        sale(rep, othersCustomer, product, 1, "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is not one of")));

        deactivateRep(rep);
        sale(rep, null, product, 1, "10")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is inactive")));
    }

    @Test
    void oldStyleSaleWithoutRepresentativeStillWorks() throws Exception {
        long product = stockedProduct(5);
        long customer = createCustomer("Old buyer", counterRepresentativeId());
        long saleId = read(sale(counterRepresentativeId(), customer, product, 2, "10").andReturn()).get("id").asLong();
        rawUpdate("update sale set representative_id = null where id = ?", saleId); // as before representatives existed

        mvc.perform(get("/api/sales/" + saleId).session(admin))
                .andExpect(jsonPath("$.representative").doesNotExist())
                .andExpect(jsonPath("$.customer.id").value(customer));
        mvc.perform(withJson(post("/api/sales/" + saleId + "/cancel"), Map.of("reason", "old sale")).session(admin))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- report

    @Test
    void representativeReportSummarisesSalesReturnsAndWhatIsOwed() throws Exception {
        long rep = createRep("Reported");
        createCustomer("R cust 1", rep);
        long c2 = createCustomer("R cust 2", rep);
        long product = stockedProduct(20);

        JsonNode paid = read(sale(rep, c2, product, 1, "100", "PAID").andReturn());
        sale(rep, null, product, 1, "50", "UNPAID").andExpect(status().isCreated());
        JsonNode cancelled = read(sale(rep, null, product, 1, "999", "PAID").andReturn());
        mvc.perform(withJson(post("/api/sales/" + cancelled.get("id").asLong() + "/cancel"), Map.of("reason", "x")).session(admin))
                .andExpect(status().isOk());
        mvc.perform(withJson(post("/api/sale-returns"), Map.of("documentId", paid.get("id").asLong(),
                        "returnDate", today.toString(), "reason", "damaged",
                        "items", List.of(Map.of("documentItemId", paid.get("items").get(0).get("id").asLong(), "quantity", 1)))).session(admin))
                .andExpect(status().isCreated());
        flush();

        mvc.perform(get("/api/reports/representatives").param("representativeId", String.valueOf(rep))
                        .param("from", today.toString()).param("to", today.toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customers").value(2))
                .andExpect(jsonPath("$.content[0].invoices").value(2))
                .andExpect(jsonPath("$.content[0].salesTotal").value(150.00))
                .andExpect(jsonPath("$.content[0].returns").value(100.00))
                .andExpect(jsonPath("$.content[0].netSales").value(50.00))
                .andExpect(jsonPath("$.content[0].paid").value(100.00))
                .andExpect(jsonPath("$.content[0].owed").value(50.00))
                .andExpect(jsonPath("$.content[0].lastSale").value(today.toString()));

        // Without a representative filter, the representative rows (incl. "Unassigned") add up to the Sales report.
        JsonNode all = read(mvc.perform(get("/api/reports/representatives").param("from", today.toString())
                .param("to", today.toString()).param("size", "200").session(admin)).andReturn());
        JsonNode salesReport = read(mvc.perform(get("/api/reports/sales").param("from", today.toString())
                .param("to", today.toString()).session(admin)).andReturn());
        org.assertj.core.api.Assertions.assertThat(all.get("summary").get("salesTotal").decimalValue())
                .isEqualByComparingTo(salesReport.get("summary").get("total").decimalValue());
        org.assertj.core.api.Assertions.assertThat(all.get("summary").get("invoices").asLong())
                .isEqualTo(salesReport.get("summary").get("documents").asLong());

        mvc.perform(get("/api/reports/sales").param("representativeId", String.valueOf(rep)).session(admin))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].party").exists());
    }

    @Test
    void unassignedRowCoversOldSales() throws Exception {
        long product = stockedProduct(5);
        long customer = createCustomer("Unassigned buyer", counterRepresentativeId());
        long saleId = read(sale(counterRepresentativeId(), customer, product, 1, "40").andReturn()).get("id").asLong();
        rawUpdate("update sale set representative_id = null where id = ?", saleId);

        mvc.perform(get("/api/reports/representatives").param("size", "200").param("sort", "name,desc").session(admin))
                .andExpect(jsonPath("$.content[?(@.name == 'Unassigned')]").exists());
        mvc.perform(get("/api/reports/representatives").param("active", "true").param("size", "200").session(admin))
                .andExpect(jsonPath("$.content[?(@.name == 'Unassigned')]").doesNotExist());
    }

    // ---------------------------------------------------------------- permissions

    @Test
    void permissions() throws Exception {
        long rep = createRep("Perm");
        MockHttpSession normal = login(testUsers.normalUser().getUsername());

        mvc.perform(get("/api/representatives").session(normal)).andExpect(status().isOk());
        mvc.perform(get("/api/representatives/counter").session(normal)).andExpect(status().isOk());
        mvc.perform(withJson(post("/api/representatives"), Map.of("name", "Nope")).session(normal)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/representatives/" + rep + "/move-customers"), Map.of("targetRepresentativeId", rep))
                .session(normal)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/representatives").session(normal)).andExpect(status().isForbidden());
        // Cashiers can still sell to any active representative.
        sale(rep, null, stockedProduct(2), 1, "10", "PAID", normal).andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- helpers

    private long createRep(String prefix) throws Exception {
        return read(mvc.perform(withJson(post("/api/representatives"), Map.of("name", TestUsers.uniqueName(prefix),
                        "area", prefix + " area")).session(admin))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private void deactivateRep(long id) throws Exception {
        JsonNode r = read(mvc.perform(get("/api/representatives/" + id).session(admin)).andReturn());
        mvc.perform(withJson(put("/api/representatives/" + id), Map.of("name", r.get("name").asText(), "active", false,
                "version", r.get("version").asLong())).session(admin)).andExpect(status().isOk());
    }

    private long createCustomer(String name, long rep) throws Exception {
        return read(mvc.perform(withJson(post("/api/customers"), Map.of("name", name, "representativeId", rep)).session(admin))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long stockedProduct(int qty) throws Exception {
        long id = read(mvc.perform(withJson(post("/api/products"), Map.of("title", TestUsers.uniqueName("RepBook"),
                "purchasePrice", 10, "sellingPrice", 20)).session(admin)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
        mvc.perform(withJson(post("/api/stock-adjustments"), Map.of("productId", id, "adjustmentQuantity", qty,
                "expectedCurrentQuantity", 0, "reason", "FOUND")).session(admin)).andExpect(status().isCreated());
        return id;
    }

    private Map<String, Object> saleBody(Long rep, Long customer, long product, int qty, String price) {
        Map<String, Object> body = new HashMap<>();
        body.put("representativeId", rep);
        body.put("customerId", customer);
        body.put("saleDate", today.toString());
        body.put("paymentStatus", "PAID");
        body.put("items", List.of(Map.of("productId", product, "quantity", qty, "unitPrice", price)));
        return body;
    }

    private ResultActions sale(long rep, Long customer, long product, int qty, String price) throws Exception {
        return sale(rep, customer, product, qty, price, "PAID");
    }

    private ResultActions sale(long rep, Long customer, long product, int qty, String price, String payment) throws Exception {
        return sale(rep, customer, product, qty, price, payment, admin);
    }

    private ResultActions sale(long rep, Long customer, long product, int qty, String price, String payment,
                               MockHttpSession session) throws Exception {
        Map<String, Object> body = saleBody(rep, customer, product, qty, price);
        body.put("paymentStatus", payment);
        return mvc.perform(withJson(post("/api/sales"), body).session(session));
    }

    private void flush() {
        entityManager.flush();
    }

    /** Simulates data from before representatives existed; the shared test session must forget its cached copies. */
    private void rawUpdate(String sql, Object... args) {
        entityManager.flush();
        jdbc.update(sql, args);
        entityManager.clear();
    }
}
