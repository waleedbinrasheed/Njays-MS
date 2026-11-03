package com.inventory.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared setup for the tailoring ITs: an administrator session and helpers that call the real API. */
public abstract class TailoringIT extends ApiTestBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ZoneId businessZone;

    protected MockHttpSession admin;
    protected long adminId;
    protected LocalDate today;

    @BeforeEach
    protected void signInAsAdmin() throws Exception {
        var user = testUsers.admin();
        adminId = user.getId();
        admin = login(user.getUsername());
        today = LocalDate.now(businessZone);
    }

    /** A JSON object that may contain nulls (Map.of does not allow them). */
    protected static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    protected static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    protected long createFabric(String code, String name, String cost, String price, String reorder) throws Exception {
        MvcResult result = mvc.perform(withJson(post("/api/fabrics"), body(
                        "code", code, "name", name, "colour", "Navy", "material", "Cotton",
                        "costPerMetre", d(cost), "pricePerMetre", d(price), "reorderLevel", d(reorder)))
                        .session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    protected long createCustomer(String name, String phone) throws Exception {
        MvcResult result = mvc.perform(withJson(post("/api/customers"), body("name", name, "phone", phone))
                        .session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    /** Points are [label, sortOrder] pairs. */
    protected JsonNode createGarmentType(String name, String shape, String... labels) throws Exception {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            points.add(body("label", labels[i], "sortOrder", i + 1, "active", true));
        }
        MvcResult result = mvc.perform(withJson(post("/api/garment-types"), body(
                        "name", name, "description", null, "shape", shape, "active", true, "points", points))
                        .session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result);
    }

    /** Current stock of a fabric in metres. */
    protected BigDecimal stockOf(long productId) throws Exception {
        MvcResult result = mvc.perform(get("/api/inventory/" + productId).session(admin))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("quantity").decimalValue();
    }

    /** Stock-in of a fabric (reason STOCK_IN) with the current quantity as the expected value. */
    protected void stockIn(long productId, String metres) throws Exception {
        BigDecimal current = stockOf(productId);
        mvc.perform(withJson(post("/api/stock-adjustments"), body(
                        "productId", productId, "adjustmentQuantity", d(metres),
                        "expectedCurrentQuantity", current, "reason", "STOCK_IN", "notes", "Delivery"))
                        .session(admin))
                .andExpect(status().isCreated());
    }

    protected static Map<String, Object> stockLine(long garmentTypeId, long fabricId, String metres, String stitching) {
        return body("garmentTypeId", garmentTypeId, "fabricSource", "STOCK", "fabricProductId", fabricId,
                "fabricMetres", d(metres), "stitchingCharge", d(stitching));
    }

    protected static Map<String, Object> customerLine(long garmentTypeId, String stitching) {
        return body("garmentTypeId", garmentTypeId, "fabricSource", "CUSTOMER", "customerFabricNote", "Customer's silk",
                "stitchingCharge", d(stitching));
    }

    @SafeVarargs
    protected static Map<String, Object> orderBody(long customerId, String dueDate, Map<String, Object>... lines) {
        return body("customerId", customerId, "dueDate", dueDate, "lines", List.of(lines));
    }

    protected JsonNode createOrder(Map<String, Object> orderBody) throws Exception {
        MvcResult result = mvc.perform(withJson(post("/api/orders"), orderBody).session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result);
    }

    protected long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    protected String dueInDays(int days) {
        return today.plusDays(days).toString();
    }
}
