package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.document.DocumentDtos.CancelRequest;
import com.inventory.dto.document.DocumentDtos.PaymentStatusRequest;
import com.inventory.dto.sale.SaleDtos.CreateSaleRequest;
import com.inventory.dto.sale.SaleDtos.SaleDetail;
import com.inventory.dto.sale.SaleDtos.SaleSummary;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.service.SaleService;
import com.inventory.service.SaleService.SaleFilter;
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
@RequestMapping("/api/sales")
public class SaleController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "saleDate", "saleDate",
            "invoiceNumber", "invoiceNumber",
            "total", "total",
            "customer", "customer.name",
            "createdAt", "createdAt",
            "id", "id"));

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public PageResponse<SaleSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Long createdBy,
            @SortDefault.SortDefaults({@SortDefault(sort = "saleDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) @PageableDefault Pageable pageable) {
        return service.search(new SaleFilter(search, customerId, productId, from, to, paymentStatus, status, createdBy),
                SORTABLE.check(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_VIEW')")
    public SaleDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ResponseEntity<SaleDetail> create(@Valid @RequestBody CreateSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}/payment-status")
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public SaleDetail paymentStatus(@PathVariable Long id, @Valid @RequestBody PaymentStatusRequest request) {
        return service.updatePaymentStatus(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALE_CANCEL')")
    public SaleDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request);
    }
}
