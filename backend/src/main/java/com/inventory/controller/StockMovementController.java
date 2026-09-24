package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.inventory.InventoryDtos.StockAdjustmentRequest;
import com.inventory.dto.inventory.InventoryDtos.StockAdjustmentResponse;
import com.inventory.dto.inventory.InventoryDtos.StockTransactionResponse;
import com.inventory.entity.TransactionType;
import com.inventory.service.StockAdjustmentService;
import com.inventory.service.StockQueryService;
import com.inventory.service.StockQueryService.MovementFilter;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Stock history (read-only ledger) and manual adjustments. */
@RestController
public class StockMovementController {

    private static final SortWhitelist SORTABLE = SortWhitelist.of("createdAt", "id");

    private final StockQueryService queries;
    private final StockAdjustmentService adjustments;

    public StockMovementController(StockQueryService queries, StockAdjustmentService adjustments) {
        this.queries = queries;
        this.adjustments = adjustments;
    }

    /** Newest first by default; {@code from}/{@code to} are inclusive business dates (yyyy-MM-dd). */
    @GetMapping("/api/stock-transactions")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public PageResponse<StockTransactionResponse> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50)
            @SortDefault.SortDefaults({@SortDefault(sort = "createdAt", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) Pageable pageable) {
        return queries.movements(new MovementFilter(productId, type, from, to), SORTABLE.check(pageable));
    }

    @PostMapping("/api/stock-adjustments")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public ResponseEntity<StockAdjustmentResponse> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adjustments.adjust(request));
    }

    @GetMapping("/api/stock-adjustments")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public PageResponse<StockAdjustmentResponse> adjustments(
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({@SortDefault(sort = "createdAt", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) Pageable pageable) {
        return adjustments.list(productId, SORTABLE.check(pageable));
    }
}
