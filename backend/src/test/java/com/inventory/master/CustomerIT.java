package com.inventory.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.TailoringIT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerIT extends TailoringIT {

    @Test
    void activeCustomerWithSameNameAndPhoneIsRefused() throws Exception {
        createCustomer("Ayesha Khan", "0300-1234567");

        mvc.perform(withJson(post("/api/customers"), body("name", "AYESHA KHAN", "phone", "0300-1234567"))
                        .session(admin))
                .andExpect(status().isConflict());

        // Same name with another phone number is a different person.
        createCustomer("Ayesha Khan", "0321-7654321");
        // An inactive duplicate is allowed.
        mvc.perform(withJson(post("/api/customers"), body("name", "Ayesha Khan", "phone", "0300-1234567",
                        "active", false)).session(admin))
                .andExpect(status().isCreated());
    }

    @Test
    void walkInCustomerIsNeverListedOrReturned() throws Exception {
        mvc.perform(get("/api/customers").param("search", "walk-in").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        long walkIn = jdbc.queryForObject("select id from customer where walk_in", Long.class);
        mvc.perform(get("/api/customers/" + walkIn).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void customerWithMeasurementsCannotBeDeletedButCanBeDeactivated() throws Exception {
        long customer = createCustomer("Bilal Ahmed", "0300-0000001");
        JsonNode type = createGarmentType("Shirt", "Chest");
        long pointId = type.get("points").get(0).get("id").asLong();
        mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", type.get("id").asLong(),
                        "values", List.of(body("pointId", pointId, "value", d("40")))))
                .session(admin))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/customers/" + customer).with(csrf()).session(admin))
                .andExpect(status().isConflict());

        long version = version("/api/customers/" + customer);
        mvc.perform(withJson(put("/api/customers/" + customer), body(
                        "name", "Bilal Ahmed", "phone", "0300-0000001", "active", false, "version", version))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateWithAnOutdatedVersionIsRefused() throws Exception {
        long customer = createCustomer("Sara Malik", "0300-0000002");
        mvc.perform(withJson(put("/api/customers/" + customer), body(
                        "name", "Sara Malik", "phone", "0300-0000002", "active", true, "version", 99))
                        .session(admin))
                .andExpect(status().isConflict());
    }

    private long version(String path) throws Exception {
        return read(mvc.perform(get(path).session(admin)).andReturn()).get("version").asLong();
    }
}
