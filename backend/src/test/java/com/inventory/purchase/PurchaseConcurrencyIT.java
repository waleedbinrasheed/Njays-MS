package com.inventory.purchase;

import com.inventory.dto.master.MasterDataDtos.PartyRequest;
import com.inventory.dto.master.MasterDataDtos.ProductRequest;
import com.inventory.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseLineRequest;
import com.inventory.entity.PaymentStatus;
import com.inventory.exception.BusinessException;
import com.inventory.security.AppUserDetailsService;
import com.inventory.security.AppUserPrincipal;
import com.inventory.service.ProductService;
import com.inventory.service.PurchaseService;
import com.inventory.service.SupplierService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real commits: numbering and rollback behaviour cannot be checked inside a rolled-back test transaction. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class PurchaseConcurrencyIT {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private ProductService productService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private TestUsers testUsers;

    @Autowired
    private AppUserDetailsService userDetails;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ZoneId businessZone;

    private AppUserPrincipal actor;
    private long supplierId;
    private long productId;

    @BeforeEach
    void setUp() {
        actor = userDetails.loadById(testUsers.admin().getId()).orElseThrow();
        signIn();
        supplierId = supplierService.create(new PartyRequest(TestUsers.uniqueName("ConcSupplier"), null, null, null, null,
                true, null)).id();
        productId = productService.create(new ProductRequest(null, TestUsers.uniqueName("ConcBook"), null, null, null,
                null, null, BigDecimal.ONE, BigDecimal.TEN, true, null)).id();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentPurchasesGetUniqueConsecutiveNumbersAndAllStock() throws Exception {
        int count = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                signIn();
                start.await();
                try {
                    return purchaseService.create(request(2, null)).purchaseNumber();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        List<Integer> sequence = new ArrayList<>();
        for (Future<String> f : futures) {
            String n = f.get(60, TimeUnit.SECONDS);
            sequence.add(Integer.parseInt(n.substring(n.lastIndexOf('-') + 1)));
        }
        pool.shutdown();

        assertThat(sequence).doesNotHaveDuplicates();
        int min = sequence.stream().min(Integer::compare).orElseThrow();
        assertThat(sequence).containsExactlyInAnyOrderElementsOf(
                java.util.stream.IntStream.range(min, min + count).boxed().toList());
        assertThat(stock()).isEqualTo(2 * count);
    }

    @Test
    void failedPurchaseLeavesNoTraceAndDoesNotConsumeANumber() {
        String before = purchaseService.create(request(1, null)).purchaseNumber();

        // Fails after the number was drawn (invoice discount larger than the total).
        assertThatThrownBy(() -> purchaseService.create(request(1, new BigDecimal("1000"))))
                .isInstanceOf(BusinessException.class);

        String after = purchaseService.create(request(1, null)).purchaseNumber();
        assertThat(seq(after)).isEqualTo(seq(before) + 1);
        assertThat(stock()).isEqualTo(2);
        Long purchases = jdbc.queryForObject("select count(*) from purchase where supplier_id = ?", Long.class, supplierId);
        assertThat(purchases).isEqualTo(2);
    }

    private CreatePurchaseRequest request(int qty, BigDecimal invoiceDiscount) {
        return new CreatePurchaseRequest(supplierId, LocalDate.now(businessZone), null, PaymentStatus.PAID, invoiceDiscount,
                null, List.of(new PurchaseLineRequest(productId, qty, new BigDecimal("5.00"), null, null)));
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, actor.getAuthorities()));
    }

    private int stock() {
        Integer q = jdbc.queryForObject("select quantity from inventory where product_id = ?", Integer.class, productId);
        return q == null ? -1 : q;
    }

    private static int seq(String number) {
        return Integer.parseInt(number.substring(number.lastIndexOf('-') + 1));
    }
}
