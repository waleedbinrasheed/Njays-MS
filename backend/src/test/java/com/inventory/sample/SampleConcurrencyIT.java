package com.inventory.sample;

import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.master.MasterDataDtos.RepresentativeRequest;
import com.inventory.dto.sample.SampleDtos.CreateSampleRequest;
import com.inventory.dto.sample.SampleDtos.CreateSampleReturnRequest;
import com.inventory.dto.sample.SampleDtos.SampleDetail;
import com.inventory.dto.sample.SampleDtos.SampleLineRequest;
import com.inventory.dto.sample.SampleDtos.SampleReturnLineRequest;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.security.AppUserDetailsService;
import com.inventory.security.AppUserPrincipal;
import com.inventory.service.InventoryService;
import com.inventory.service.InventoryService.Movement;
import com.inventory.service.ProductService;
import com.inventory.service.RepresentativeService;
import com.inventory.service.SampleReturnService;
import com.inventory.service.SampleService;
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

/** Two clerks recording returns of the same sample at the same moment, with real commits. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class SampleConcurrencyIT {

    @Autowired
    private SampleService sampleService;

    @Autowired
    private SampleReturnService sampleReturnService;

    @Autowired
    private ProductService productService;

    @Autowired
    private RepresentativeService representativeService;

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
    void concurrentReturnsNeverExceedWhatIsOutstanding() throws Exception {
        long productId = tx.execute(s -> {
            long id = productService.create(new ProductRequest(null, TestUsers.uniqueName("SmpConc"), null, null, null,
                    null, null, BigDecimal.ONE, BigDecimal.TEN, true, null)).id();
            inventoryService.apply(new Movement(id, TransactionType.ADJUSTMENT, 10, null, null, "opening"));
            return id;
        });
        long repId = representativeService.create(new RepresentativeRequest(TestUsers.uniqueName("SmpRep"), null, null,
                null, null, true, null)).id();
        LocalDate today = LocalDate.now(businessZone);
        SampleDetail sample = sampleService.create(new CreateSampleRequest(repId, today, null,
                List.of(new SampleLineRequest(productId, 5, new BigDecimal("1.00")))));
        long itemId = sample.items().getFirst().id();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                signIn();
                start.await();
                try {
                    sampleReturnService.create(new CreateSampleReturnRequest(sample.id(), today, null,
                            List.of(new SampleReturnLineRequest(itemId, 2))));
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
        assertThat(jdbc.queryForObject("select returned_quantity from sample_issue where id = ?", Integer.class, sample.id()))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select sum(quantity) from sample_return_item where sample_issue_item_id = ?",
                Long.class, itemId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select status from sample_issue where id = ?", String.class, sample.id()))
                .isEqualTo("PARTIALLY_RETURNED");
        assertThat(jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId))
                .isEqualTo(10 - 5 + 4);
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
    }
}
