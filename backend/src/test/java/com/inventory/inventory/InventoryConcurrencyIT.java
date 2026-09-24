package com.inventory.inventory;

import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.security.AppUserDetailsService;
import com.inventory.security.AppUserPrincipal;
import com.inventory.service.InventoryService;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.ProductService;
import com.inventory.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real commits, real concurrent transactions: inventory must stay consistent no matter how
 * requests interleave. Not @Transactional on purpose.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class InventoryConcurrencyIT {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private AppUserDetailsService userDetails;

    private AppUserPrincipal actor;

    @BeforeEach
    void actAsAdmin() {
        actor = userDetails.loadById(testUsers.admin().getId()).orElseThrow();
        signIn();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentSalesNeverOversellOrLoseUpdates() throws Exception {
        long productId = newProductWithStock(10);
        int attempts = 25;

        List<Boolean> results = runConcurrently(attempts, () -> {
            try {
                tx.executeWithoutResult(s -> inventoryService.apply(
                        new Movement(productId, TransactionType.SALE, -1, ReferenceType.SALE, null, "concurrent")));
                return true;
            } catch (BusinessException insufficient) {
                return false;
            }
        });

        assertThat(results.stream().filter(ok -> ok).count()).isEqualTo(10);
        assertThat(stock(productId)).isZero();
        assertLedgerChainIsConsistent(productId, 11);
    }

    @Test
    void concurrentMixedMovementsKeepExactTotals() throws Exception {
        long productId = newProductWithStock(50);

        List<Boolean> results = runConcurrently(40, () -> {
            int change = Thread.currentThread().threadId() % 2 == 0 ? 3 : -2;
            tx.executeWithoutResult(s -> inventoryService.apply(
                    new Movement(productId, TransactionType.ADJUSTMENT, change, null, null, "mixed")));
            return change > 0;
        });

        long ins = results.stream().filter(b -> b).count();
        long outs = results.size() - ins;
        assertThat(stock(productId)).isEqualTo(50 + 3 * ins - 2 * outs);
        assertLedgerChainIsConsistent(productId, 1 + results.size());
    }

    @Test
    void multiLineDocumentIsAllOrNothing() {
        long a = newProductWithStock(5);
        long b = newProductWithStock(1);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventoryService.applyAll(List.of(
                new Movement(a, TransactionType.SALE, -2, ReferenceType.SALE, null, null),
                new Movement(b, TransactionType.SALE, -3, ReferenceType.SALE, null, null)))))
                .isInstanceOf(BusinessException.class);

        assertThat(stock(a)).isEqualTo(5);
        assertThat(stock(b)).isEqualTo(1);
        assertLedgerChainIsConsistent(a, 1);
        assertLedgerChainIsConsistent(b, 1);
    }

    @Test
    void stockCannotBeMovedOutsideABusinessTransaction() {
        long id = newProductWithStock(1);
        assertThatThrownBy(() -> inventoryService.apply(
                new Movement(id, TransactionType.ADJUSTMENT, 1, null, null, null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(stock(id)).isEqualTo(1);
    }

    @Test
    void databaseRejectsNegativeStockEvenIfCodeIsBypassed() {
        long id = newProductWithStock(2);
        assertThatThrownBy(() -> jdbc.update("update inventory set quantity = -1 where product_id = ?", id))
                .hasMessageContaining("chk_inventory_quantity");
        assertThatThrownBy(() -> jdbc.update("""
                insert into stock_transaction (product_id, transaction_type, quantity, previous_quantity,
                                               resulting_quantity, created_by)
                values (?, 'ADJUSTMENT', 5, 2, 9, ?)""", id, actor.getId()))
                .hasMessageContaining("chk_stock_tx_arithmetic");
    }

    @Test
    void everyProductsStockEqualsItsLedgerTotal() throws Exception {
        concurrentSalesNeverOversellOrLoseUpdates();
        Long mismatches = jdbc.queryForObject("""
                select count(*) from inventory i
                where i.quantity <> coalesce((select sum(t.quantity) from stock_transaction t
                                              where t.product_id = i.product_id), 0)""", Long.class);
        assertThat(mismatches).isZero();
    }

    // --- helpers ---

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
    }

    private long newProductWithStock(int quantity) {
        return tx.execute(s -> {
            long id = productService.create(new ProductRequest(null, TestUsers.uniqueName("Concurrent"), null, null, null,
                    null, null, BigDecimal.ONE, BigDecimal.TEN, true, null)).id();
            inventoryService.apply(new Movement(id, TransactionType.ADJUSTMENT, quantity, null, null, "opening"));
            return id;
        });
    }

    private List<Boolean> runConcurrently(int count, Callable<Boolean> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> {
                    signIn();
                    start.await();
                    try {
                        return task.call();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private int stock(long productId) {
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }

    /** Each ledger row starts where the previous one ended, and the last one equals current stock. */
    private void assertLedgerChainIsConsistent(long productId, int expectedRows) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select previous_quantity, quantity, resulting_quantity from stock_transaction where product_id = ? order by id",
                productId);
        assertThat(rows).hasSize(expectedRows);
        int running = 0;
        for (Map<String, Object> r : rows) {
            assertThat(((Number) r.get("previous_quantity")).intValue()).isEqualTo(running);
            running += ((Number) r.get("quantity")).intValue();
            assertThat(((Number) r.get("resulting_quantity")).intValue()).isEqualTo(running);
        }
        assertThat(running).isEqualTo(stock(productId));
    }
}
