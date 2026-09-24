package com.inventory.sale;

import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.sale.SaleDtos.CreateSaleRequest;
import com.inventory.dto.sale.SaleDtos.SaleLineRequest;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.security.AppUserDetailsService;
import com.inventory.security.AppUserPrincipal;
import com.inventory.service.InventoryService;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Several cashiers selling the same stock at the same moment, with real commits. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class SaleConcurrencyIT {

    @Autowired
    private SaleService saleService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private AppUserDetailsService userDetails;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ZoneId businessZone;

    private AppUserPrincipal actor;

    @BeforeEach
    void setUp() {
        actor = userDetails.loadById(testUsers.admin().getId()).orElseThrow();
        signIn();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lastCopiesAreNeverSoldTwice() throws Exception {
        long productId = productWithStock(5);
        int attempts = 16; // each tries to sell 2; only two sales fit into 5
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                signIn();
                start.await();
                try {
                    saleService.create(request(productId, 2));
                    return true;
                } catch (BusinessException insufficient) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(60, TimeUnit.SECONDS)) succeeded++;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(2);
        assertThat(stock(productId)).isEqualTo(1);
        Long saleRows = jdbc.queryForObject("""
                select count(*) from sale_item i join sale s on s.id = i.sale_id where i.product_id = ?""",
                Long.class, productId);
        assertThat(saleRows).isEqualTo(2);
        Long ledgerSum = jdbc.queryForObject("select sum(quantity) from stock_transaction where product_id = ?",
                Long.class, productId);
        assertThat(ledgerSum).isEqualTo(1);
    }

    @Test
    void rejectedSaleLeavesNoInvoiceAndNoNumberGap() {
        long productId = productWithStock(3);
        String first = saleService.create(request(productId, 1)).invoiceNumber();

        try {
            saleService.create(request(productId, 50));
        } catch (BusinessException expected) {
            // insufficient stock
        }

        String second = saleService.create(request(productId, 1)).invoiceNumber();
        assertThat(seq(second)).isEqualTo(seq(first) + 1);
        assertThat(stock(productId)).isEqualTo(1);
    }

    @Test
    void multiLineSaleWithOneShortLineChangesNothing() {
        long plenty = productWithStock(10);
        long scarce = productWithStock(2);
        CreateSaleRequest both = new CreateSaleRequest(null, LocalDate.now(businessZone), PaymentStatus.PAID, null, null,
                List.of(new SaleLineRequest(plenty, 1, new BigDecimal("100.00"), null, null),
                        new SaleLineRequest(scarce, 3, new BigDecimal("100.00"), null, null)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> saleService.create(both))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Insufficient stock for '" + title(scarce) + "': available 2, requested 3");

        assertThat(stock(plenty)).isEqualTo(10);
        assertThat(stock(scarce)).isEqualTo(2);
        Long items = jdbc.queryForObject("select count(*) from sale_item where product_id in (?, ?)", Long.class, plenty, scarce);
        assertThat(items).isZero();
    }

    private String title(long productId) {
        return jdbc.queryForObject("select title from product where id = ?", String.class, productId);
    }

    private CreateSaleRequest request(long productId, int qty) {
        return new CreateSaleRequest(null, LocalDate.now(businessZone), PaymentStatus.PAID, null, null,
                List.of(new SaleLineRequest(productId, qty, new BigDecimal("100.00"), null, null)));
    }

    private long productWithStock(int qty) {
        return tx.execute(s -> {
            long id = productService.create(new ProductRequest(null, TestUsers.uniqueName("SaleConc"), null, null, null,
                    null, null, BigDecimal.ONE, BigDecimal.TEN, true, null)).id();
            inventoryService.apply(new Movement(id, TransactionType.ADJUSTMENT, qty, null, null, "opening"));
            return id;
        });
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
    }

    private int stock(long productId) {
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }

    private static int seq(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('-') + 1));
    }
}
