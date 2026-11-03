package com.inventory.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.TailoringIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIT extends TailoringIT {

    private long customer;
    private long shirt;
    private long fabric;

    /** Lawn at 100 per metre with 10 metres in stock. */
    @BeforeEach
    void setUpShop() throws Exception {
        customer = createCustomer("Hamza Ali", "0300-5550001");
        shirt = createGarmentType("Shirt", "Chest", "Length").get("id").asLong();
        fabric = createFabric("LWN-01", "Lawn", "60", "100", "2");
        stockIn(fabric, "10");
    }

    @Test
    void stockLineIssuesFabricAndPricesItFromTheFabric() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), stockLine(shirt, fabric, "2.5", "300")));

        assertThat(order.get("orderNumber").asText()).startsWith("ORD-" + today.getYear() + "-");
        assertThat(order.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("550");
        assertThat(order.get("paymentStatus").asText()).isEqualTo("UNPAID");
        JsonNode line = order.get("lines").get(0);
        assertThat(line.get("fabricAmount").decimalValue()).isEqualByComparingTo("250");
        assertThat(line.get("fabricPricePerMetre").decimalValue()).isEqualByComparingTo("100");
        assertThat(stockOf(fabric)).isEqualByComparingTo("7.5");
        assertThat(count("select count(*) from stock_transaction where reference_type = 'ORDER' and reference_id = ?",
                order.get("id").asLong())).isEqualTo(1);
    }

    @Test
    void customerSuppliedClothTouchesNoStock() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), customerLine(shirt, "400")));

        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("400");
        assertThat(order.get("lines").get(0).get("fabric").isNull()).isTrue();
        assertThat(stockOf(fabric)).isEqualByComparingTo("10");
    }

    @Test
    void insufficientStockRefusesTheOrderAndTakesNoFabric() throws Exception {
        mvc.perform(withJson(post("/api/orders"), orderBody(customer, dueInDays(10),
                        stockLine(shirt, fabric, "11", "0")))
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for 'Lawn': available 10 m, requested 11 m"));
        assertThat(stockOf(fabric)).isEqualByComparingTo("10");
    }

    @Test
    void fabricPriceChangesDoNotAlterSavedOrders() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), stockLine(shirt, fabric, "2", "0")));
        JsonNode current = read(mvc.perform(get("/api/fabrics/" + fabric).session(admin)).andReturn());

        mvc.perform(withJson(put("/api/fabrics/" + fabric), body(
                        "code", "LWN-01", "name", "Lawn", "colour", "Navy", "costPerMetre", d("60"),
                        "pricePerMetre", d("250"), "reorderLevel", d("2"), "version", current.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isOk());

        mvc.perform(get("/api/orders/" + order.get("id").asLong()).session(admin))
                .andExpect(jsonPath("$.lines[0].fabricPricePerMetre").value(100.0))
                .andExpect(jsonPath("$.total").value(200.0));
    }

    @Test
    void editingAnOrderAppliesOnlyTheChangeInFabric() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), stockLine(shirt, fabric, "2", "100")));
        assertThat(stockOf(fabric)).isEqualByComparingTo("8");

        Map<String, Object> edit = orderBody(customer, dueInDays(12), stockLine(shirt, fabric, "5", "100"));
        edit.put("version", order.get("version").asLong());
        mvc.perform(withJson(put("/api/orders/" + order.get("id").asLong()), edit).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(600.0));

        assertThat(stockOf(fabric)).isEqualByComparingTo("5");
    }

    @Test
    void cancelReturnsTheFabricToStock() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), stockLine(shirt, fabric, "3", "0")));
        long id = order.get("id").asLong();

        mvc.perform(withJson(post("/api/orders/" + id + "/cancel"), body(
                        "reason", "Customer changed mind", "version", order.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Customer changed mind"));

        assertThat(stockOf(fabric)).isEqualByComparingTo("10");
        assertThat(count("select count(*) from stock_transaction where transaction_type = 'ORDER_CANCEL' and product_id = ?",
                fabric)).isEqualTo(1);
    }

    @Test
    void cancelIsRefusedOnceAnythingIsPaid() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), customerLine(shirt, "200")));
        long id = order.get("id").asLong();
        pay(id, "50");

        mvc.perform(withJson(post("/api/orders/" + id + "/cancel"), body(
                        "reason", "Changed mind", "version", order.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Refund is not handled")));
    }

    @Test
    void statusMovesOneStageAtATimeAndIsRecorded() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), customerLine(shirt, "100")));
        long id = order.get("id").asLong();

        mvc.perform(withJson(post("/api/orders/" + id + "/status"), body(
                        "status", "TRIAL", "version", order.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isBadRequest());

        JsonNode cutting = move(id, order.get("version").asLong(), "CUTTING");
        JsonNode back = move(id, cutting.get("version").asLong(), "RECEIVED");
        assertThat(back.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(back.get("statusHistory")).hasSize(3);
    }

    @Test
    void ordersCannotBeEditedOnceInTrial() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), customerLine(shirt, "100")));
        long id = order.get("id").asLong();
        long version = order.get("version").asLong();
        version = move(id, version, "CUTTING").get("version").asLong();
        version = move(id, version, "STITCHING").get("version").asLong();
        version = move(id, version, "TRIAL").get("version").asLong();

        Map<String, Object> edit = orderBody(customer, dueInDays(12), customerLine(shirt, "150"));
        edit.put("version", version);
        mvc.perform(withJson(put("/api/orders/" + id), edit).session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void paymentsAreCappedAtTheBalanceDue() throws Exception {
        JsonNode order = createOrder(orderBody(customer, dueInDays(10), customerLine(shirt, "500")));
        long id = order.get("id").asLong();

        pay(id, "600").andExpect(status().isConflict());
        pay(id, "200")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.balance").value(300.0));
        pay(id, "300")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.balance").value(0.0));
        pay(id, "1").andExpect(status().isConflict());
    }

    @Test
    void measurementsAreCopiedOntoTheOrderLine() throws Exception {
        JsonNode kurta = createGarmentType("Kurta", "Chest", "Length");
        long chest = kurta.get("points").get(0).get("id").asLong();
        long length = kurta.get("points").get(1).get("id").asLong();
        JsonNode record = read(mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", kurta.get("id").asLong(),
                        "values", List.of(body("pointId", chest, "value", d("40")),
                                body("pointId", length, "value", d("44")))))
                        .session(admin))
                .andReturn());

        Map<String, Object> order = orderBody(customer, dueInDays(10), body(
                "garmentTypeId", kurta.get("id").asLong(), "fabricSource", "CUSTOMER",
                "measurementRecordId", record.get("id").asLong(), "stitchingCharge", d("800")));
        JsonNode line = createOrder(order).get("lines").get(0);

        assertThat(line.get("measurements")).hasSize(2);
        assertThat(line.get("measurements").get(0).get("label").asText()).isEqualTo("Chest");
        assertThat(line.get("measurements").get(0).get("value").decimalValue()).isEqualByComparingTo("40");
    }

    @Test
    void walkInCustomerCannotTakeOrders() throws Exception {
        long walkIn = jdbc.queryForObject("select id from customer where walk_in", Long.class);
        mvc.perform(withJson(post("/api/orders"), orderBody(walkIn, dueInDays(5), customerLine(shirt, "100")))
                        .session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calendarShowsDueTrialAndDeliveryDatesAndLimitsTheRange() throws Exception {
        Map<String, Object> order = orderBody(customer, dueInDays(3), customerLine(shirt, "100"));
        order.put("trialDate", dueInDays(1));
        order.put("deliveryDate", dueInDays(4));
        createOrder(order);

        mvc.perform(get("/api/orders/calendar").param("from", today.toString())
                        .param("to", dueInDays(7)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].dateType", hasItems("DUE", "TRIAL", "DELIVERY")));

        mvc.perform(get("/api/orders/calendar").param("from", today.toString())
                        .param("to", dueInDays(100)).session(admin))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode move(long id, long version, String status) throws Exception {
        return read(mvc.perform(withJson(post("/api/orders/" + id + "/status"), body(
                        "status", status, "version", version))
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions pay(long id, String amount) throws Exception {
        return mvc.perform(withJson(post("/api/orders/" + id + "/payments"), body(
                        "kind", "PAYMENT", "amount", d(amount), "method", "CASH"))
                        .session(admin));
    }
}
