package com.inventory.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.TailoringIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportIT extends TailoringIT {

    @Test
    void ordersReportCountsOnlyLiveOrdersInTheTotals() throws Exception {
        long customer = createCustomer("Report Customer", "0300-7000001");
        long shirt = createGarmentType("Report Shirt", "TOP", "Chest").get("id").asLong();
        createOrder(orderBody(customer, dueInDays(5), customerLine(shirt, "200")));
        JsonNode cancelled = createOrder(orderBody(customer, dueInDays(6), customerLine(shirt, "300")));

        mvc.perform(withJson(post("/api/orders/" + cancelled.get("id").asLong() + "/cancel"), body(
                        "reason", "Duplicate", "version", cancelled.get("version").asLong()))
                        .session(admin))
                .andExpect(status().isOk());

        mvc.perform(get("/api/reports/orders").param("customerId", String.valueOf(customer)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totals.orders").value(1))
                .andExpect(jsonPath("$.totals.total").value(200.0))
                .andExpect(jsonPath("$.totals.balance").value(200.0));
    }

    @Test
    void fabricStockReportValuesStockAtCost() throws Exception {
        long fabric = createFabric("RPT-01", "Report Wool", "40", "90", "0");
        stockIn(fabric, "10");

        mvc.perform(get("/api/reports/fabric-stock").param("search", "RPT-01").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.fabrics").value(1))
                .andExpect(jsonPath("$.totals.quantityOnHand").value(10.0))
                .andExpect(jsonPath("$.totals.costValue").value(400.0));
    }

    @Test
    void dashboardShowsOrderFiguresToAnOrderViewer() throws Exception {
        mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueToday").isNumber())
                .andExpect(jsonPath("$.overdue").isNumber())
                .andExpect(jsonPath("$.inProgress.RECEIVED").isNumber())
                .andExpect(jsonPath("$.lowStockFabrics").isNumber());
    }
}
