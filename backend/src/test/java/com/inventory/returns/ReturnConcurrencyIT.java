package com.inventory.returns;

import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.returns.ReturnDtos.CreateReturnRequest;
import com.inventory.dto.returns.ReturnDtos.ReturnLineRequest;
import com.inventory.dto.sale.SaleDtos.CreateSaleRequest;
import com.inventory.dto.sale.SaleDtos.SaleDetail;
import com.inventory.dto.sale.SaleDtos.SaleLineRequest;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.security.AppUserDetailsService;
import com.inventory.security.AppUserPrincipal;
import com.inventory.service.InventoryService;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.ProductService;
import com.inventory.service.SaleReturnService;
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

/** Two clerks returning items of the same invoice at the same moment, with real commits. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class ReturnConcurrencyIT {

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleReturnService saleReturnService;

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
    void concurrentReturnsNeverExceedWhatWasSold() throws Exception {
        long productId = tx.execute(s -> {
            long id = productService.create(new ProductRequest(null, TestUsers.uniqueName("RetConc"), null, null, null,
                    null, null, BigDecimal.ONE, BigDecimal.TEN, true, null)).id();
            inventoryService.apply(new Movement(id, TransactionType.ADJUSTMENT, 10, null, null, "opening"));
            return id;
        });
        LocalDate today = LocalDate.now(businessZone);
        SaleDetail sale = saleService.create(new CreateSaleRequest(null, today, PaymentStatus.PAID, null, null,
                List.of(new SaleLineRequest(productId, 5, new BigDecimal("10.00"), null, null))));
        long itemId = sale.items().getFirst().id();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                signIn();
                start.await();
                try {
                    saleReturnService.create(new CreateReturnRequest(sale.id(), today, "concurrent", null,
                            List.of(new ReturnLineRequest(itemId, 2))));
                    return true;
                } catch (BusinessException tooMany) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(60, TimeUnit.SECONDS)) ok++;
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(2); // 2 + 2 = 4 of 5; a third would exceed
        Long returned = jdbc.queryForObject("select sum(quantity) from sale_return_item where sale_item_id = ?",
                Long.class, itemId);
        assertThat(returned).isEqualTo(4);
        Integer stock = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        assertThat(stock).isEqualTo(10 - 5 + 4);
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
    }
}
