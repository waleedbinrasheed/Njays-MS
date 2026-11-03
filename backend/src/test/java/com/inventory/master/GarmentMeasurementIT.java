package com.inventory.master;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventory.support.TailoringIT;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Garment types with their measurement points, and measurement records (create only). */
class GarmentMeasurementIT extends TailoringIT {

    @Test
    void usedPointsAreDeactivatedAndUnusedPointsAreDeleted() throws Exception {
        JsonNode trouser = createGarmentType("Trouser", "BOTTOM", "Waist", "Hip", "Inseam");
        long customer = createCustomer("Ahmed Raza", "0300-0000010");
        long waist = pointNamed(trouser, "Waist").get("id").asLong();
        long hip = pointNamed(trouser, "Hip").get("id").asLong();
        takeMeasurement(customer, trouser.get("id").asLong(), Map.of(waist, "32", hip, "38"));

        // Keep Waist, drop Hip (used by a record) and Inseam (never used).
        long version = trouser.get("version").asLong();
        JsonNode updated = read(mvc.perform(withJson(put("/api/garment-types/" + trouser.get("id").asLong()), body(
                        "name", "Trouser", "description", null, "shape", "BOTTOM", "active", true, "version", version,
                        "points", List.of(body("id", waist, "label", "Waist", "sortOrder", 1, "active", true))))
                        .session(admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(updated.get("points")).hasSize(2);
        assertThat(pointNamed(updated, "Waist").get("active").asBoolean()).isTrue();
        assertThat(pointNamed(updated, "Hip").get("active").asBoolean()).isFalse();
        assertThat(pointNamed(updated, "Inseam")).isNull();
    }

    @Test
    void measurementUsesOnlyActivePointsOfItsGarmentType() throws Exception {
        JsonNode shirt = createGarmentType("Shirt", "TOP", "Chest", "Sleeve");
        JsonNode trouser = createGarmentType("Trouser", "BOTTOM", "Waist");
        long customer = createCustomer("Farhan Ali", "0300-0000011");
        long chest = pointNamed(shirt, "Chest").get("id").asLong();
        long sleeve = pointNamed(shirt, "Sleeve").get("id").asLong();

        // A point of another garment type is refused.
        mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", trouser.get("id").asLong(),
                        "values", List.of(body("pointId", chest, "value", d("40")))))
                        .session(admin))
                .andExpect(status().isBadRequest());

        // At least one value is required, and the date cannot be in the future.
        mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", shirt.get("id").asLong(), "values", List.of()))
                        .session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", shirt.get("id").asLong(), "takenOn", dueInDays(3),
                        "values", List.of(body("pointId", chest, "value", d("40")))))
                        .session(admin))
                .andExpect(status().isBadRequest());

        // A deactivated point is hidden from new measurements.
        mvc.perform(withJson(put("/api/garment-types/" + shirt.get("id").asLong()), body(
                        "name", "Shirt", "shape", "TOP", "active", true, "version", shirt.get("version").asLong(),
                        "points", List.of(
                                body("id", chest, "label", "Chest", "sortOrder", 1, "active", false),
                                body("id", sleeve, "label", "Sleeve", "sortOrder", 2, "active", true))))
                        .session(admin))
                .andExpect(status().isOk());
        mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", shirt.get("id").asLong(),
                        "values", List.of(body("pointId", chest, "value", d("40")))))
                        .session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void measurementRecordsAreNeverEditedOrDeleted() throws Exception {
        JsonNode shirt = createGarmentType("Kameez", "FULL", "Length");
        long customer = createCustomer("Usman Tariq", "0300-0000012");
        JsonNode record = takeMeasurement(customer, shirt.get("id").asLong(),
                Map.of(pointNamed(shirt, "Length").get("id").asLong(), "44"));
        String path = "/api/measurements/" + record.get("id").asLong();

        mvc.perform(withJson(put(path), body("notes", "changed")).session(admin))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(path).with(csrf()).session(admin))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void garmentTypeInUseCannotBeDeleted() throws Exception {
        JsonNode shirt = createGarmentType("Kurta", "TOP", "Chest");
        long customer = createCustomer("Zain Abbas", "0300-0000013");
        takeMeasurement(customer, shirt.get("id").asLong(), Map.of(pointNamed(shirt, "Chest").get("id").asLong(), "42"));

        mvc.perform(delete("/api/garment-types/" + shirt.get("id").asLong()).with(csrf()).session(admin))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode pointNamed(JsonNode type, String label) {
        for (JsonNode point : type.get("points")) {
            if (label.equals(point.get("label").asText())) return point;
        }
        return null;
    }

    private JsonNode takeMeasurement(long customer, long garmentTypeId, Map<Long, String> values) throws Exception {
        List<Map<String, Object>> entries = values.entrySet().stream()
                .map(e -> body("pointId", e.getKey(), "value", d(e.getValue())))
                .toList();
        return read(mvc.perform(withJson(post("/api/customers/" + customer + "/measurements"), body(
                        "garmentTypeId", garmentTypeId, "notes", "Taken in shop", "values", entries))
                        .session(admin))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
