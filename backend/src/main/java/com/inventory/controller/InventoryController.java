package com.inventory.controller;

import com.inventory.dto.PageResponse;
import com.inventory.dto.inventory.InventoryDtos.InventoryItem;
import com.inventory.dto.inventory.InventoryDtos.InventorySummary;
import com.inventory.dto.inventory.InventoryDtos.ReorderLevelRequest;
import com.inventory.repository.InventorySpecifications.StockStatus;
import com.inventory.service.StockAdjustmentService;
import com.inventory.service.StockQueryService;
import com.inventory.service.StockQueryService.InventoryFilter;
import com.inventory.validation.SortWhitelist;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Current stock per product. Stock itself changes only through purchases, sales, returns and adjustments. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private static final SortWhitelist SORTABLE = SortWhitelist.aliased(Map.of(
            "title", "product.title",
            "isbn", "product.isbn",
            "author", "product.author",
            "quantity", "quantity",
            "reorderLevel", "reorderLevel",
            "updatedAt", "updatedAt"));

    private final StockQueryService queries;
    private final StockAdjustmentService adjustments;

    public InventoryController(StockQueryService queries, StockAdjustmentService adjustments) {
        this.queries = queries;
        this.adjustments = adjustments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public PageResponse<InventoryItem> list(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Long publisherId,
                                            @RequestParam(required = false) StockStatus status,
                                            @RequestParam(required = false) Boolean active,
                                            @PageableDefault(sort = "title") Pageable pageable) {
        return queries.inventory(new InventoryFilter(search, null, categoryId, publisherId, status, active),
                SORTABLE.check(pageable));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public InventorySummary summary() {
        return queries.summary();
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public InventoryItem get(@PathVariable Long productId) {
        return queries.item(productId);
    }

    @PutMapping("/{productId}/reorder-level")
    @PreAuthorize("hasAuthority('STOCK_ADJUST')")
    public InventoryItem reorderLevel(@PathVariable Long productId, @Valid @RequestBody ReorderLevelRequest request) {
        return adjustments.changeReorderLevel(productId, request);
    }
}
