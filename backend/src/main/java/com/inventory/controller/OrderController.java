package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.order.OrderDtos.CalendarItem;
import com.inventory.dto.order.OrderDtos.CancelRequest;
import com.inventory.dto.order.OrderDtos.OrderRequest;
import com.inventory.dto.order.OrderDtos.OrderResponse;
import com.inventory.dto.order.OrderDtos.OrderSummary;
import com.inventory.dto.order.OrderDtos.PaymentRequest;
import com.inventory.dto.order.OrderDtos.StatusRequest;
import com.inventory.entity.OrderStatus;
import com.inventory.service.OrderService;
import com.inventory.service.OrderService.OrderFilter;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import java.util.List;

/** Garment orders: take, change (while early in production), progress, cancel, pay. Orders are never deleted. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final SortWhitelist SORTABLE =
            SortWhitelist.of("orderNumber", "dueDate", "orderDate", "total", "status");

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public PageResponse<OrderSummary> list(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) OrderStatus status,
                                           @RequestParam(required = false) Long customerId,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
                                           @PageableDefault(sort = "dueDate") Pageable pageable) {
        return service.list(new OrderFilter(search, status, customerId, dueFrom, dueTo), SORTABLE.check(pageable));
    }

    /** Orders with a due, trial or delivery date in the range (at most 93 days). */
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public List<CalendarItem> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calendar(from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public OrderResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public OrderResponse update(@PathVariable Long id, @Valid @RequestBody OrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    public OrderResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return service.recordPayment(id, request);
    }
}
