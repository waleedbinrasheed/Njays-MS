package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.purchase.PurchaseDtos.CancelRequest;
import com.inventory.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.inventory.dto.purchase.PurchaseDtos.PaymentStatusRequest;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseDetail;
import com.inventory.dto.purchase.PurchaseDtos.PurchaseSummary;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.service.PurchaseService;
import com.inventory.service.PurchaseService.PurchaseFilter;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "purchaseDate", "purchaseDate",
            "purchaseNumber", "purchaseNumber",
            "total", "total",
            "supplier", "supplier.name",
            "createdAt", "createdAt",
            "id", "id"));

    private final PurchaseService service;

    public PurchaseController(PurchaseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PageResponse<PurchaseSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) DocumentStatus status,
            @SortDefault.SortDefaults({@SortDefault(sort = "purchaseDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) @PageableDefault Pageable pageable) {
        return service.search(new PurchaseFilter(search, supplierId, productId, from, to, paymentStatus, status),
                SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_VIEW')")
    public PurchaseDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public ResponseEntity<PurchaseDetail> create(@Valid @RequestBody CreatePurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}/payment-status")
    @PreAuthorize("hasAuthority('PURCHASE_CREATE')")
    public PurchaseDetail paymentStatus(@PathVariable Long id, @Valid @RequestBody PaymentStatusRequest request) {
        return service.updatePaymentStatus(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_CANCEL')")
    public PurchaseDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request);
    }
}
