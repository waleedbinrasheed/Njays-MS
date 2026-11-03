package com.inventory.master;

import com.inventory.support.TailoringIT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fabrics (products of type FABRIC), their stock row, decimal stock-in and the stock ledger. */
class FabricIT extends TailoringIT {

    @Test
    void newFabricStartsWithZeroMetresAndItsReorderLevel() throws Exception {
        long id = createFabric("F-001", "Lawn", "200", "350", "5");
        mvc.perform(get("/api/inventory/" + id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0.0))
                .andExpect(jsonPath("$.reorderLevel").value(5.0))
                .andExpect(jsonPath("$.stockLevel").value("OUT_OF_STOCK"));
    }

    @Test
    void stockInTakesDecimalMetresAndWritesTheLedger() throws Exception {
        long id = createFabric("F-002", "Silk", "900", "1500", "0");
        stockIn(id, "12.5");
        stockIn(id, "2.125");

        assertThat(stockOf(id)).isEqualByComparingTo("14.625");
        assertThat(count("select count(*) from stock_transaction where product_id = ? and transaction_type = 'ADJUSTMENT'",
                id)).isEqualTo(2);
    }

    @Test
    void stockInMustAddStock() throws Exception {
        long id = createFabric("F-003", "Denim", "300", "500", "0");
        mvc.perform(withJson(post("/api/stock-adjustments"), body(
                        "productId", id, "adjustmentQuantity", d("-1"), "expectedCurrentQuantity", d("0"),
                        "reason", "STOCK_IN"))
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stock-in must add stock: enter a positive quantity"));
    }

    @Test
    void metresHaveAtMostThreeDecimals() throws Exception {
        long id = createFabric("F-004", "Linen", "300", "500", "0");
        mvc.perform(withJson(post("/api/stock-adjustments"), body(
                        "productId", id, "adjustmentQuantity", d("1.2345"), "expectedCurrentQuantity", d("0"),
                        "reason", "STOCK_IN"))
                        .session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fabricCodeIsUnique() throws Exception {
        createFabric("F-005", "Wool", "300", "500", "0");
        mvc.perform(withJson(post("/api/fabrics"), body("code", "f-005", "name", "Wool again",
                        "costPerMetre", d("1"), "pricePerMetre", d("2")))
                        .session(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void fabricWithStockCannotBeDeletedButAnUnusedOneCan() throws Exception {
        long used = createFabric("F-006", "Used", "100", "200", "0");
        stockIn(used, "1");
        mvc.perform(delete("/api/fabrics/" + used).with(csrf()).session(admin))
                .andExpect(status().isConflict());

        long unused = createFabric("F-007", "Unused", "100", "200", "0");
        mvc.perform(delete("/api/fabrics/" + unused).with(csrf()).session(admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void lowStockFilterUsesTheReorderLevel() throws Exception {
        createFabric("F-LOW", "Empty", "100", "200", "5");
        long ok = createFabric("F-OK", "Stocked", "100", "200", "5");
        stockIn(ok, "10");

        mvc.perform(get("/api/fabrics").param("lowStockOnly", "true").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].code", hasItem("F-LOW")))
                .andExpect(jsonPath("$.content[*].code", not(hasItem("F-OK"))));
    }

    @Test
    void reorderLevelCanBeChanged() throws Exception {
        long id = createFabric("F-008", "Cotton", "100", "200", "1");
        mvc.perform(withJson(put("/api/inventory/" + id + "/reorder-level"), body("reorderLevel", d("9")))
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reorderLevel").value(9.0));
    }
}
