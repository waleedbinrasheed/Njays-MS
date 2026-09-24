package com.inventory.service;

import com.inventory.dto.PageResponse;
import com.inventory.dto.inventory.InventoryDtos.InventoryItem;
import com.inventory.dto.inventory.InventoryDtos.ReorderLevelRequest;
import com.inventory.dto.inventory.InventoryDtos.StockAdjustmentRequest;
import com.inventory.dto.inventory.InventoryDtos.StockAdjustmentResponse;
import com.inventory.entity.AdjustmentReason;
import com.inventory.entity.AppUser;
import com.inventory.entity.Inventory;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.StockAdjustment;
import com.inventory.entity.StockTransaction;
import com.inventory.entity.TransactionType;
import com.inventory.exception.BusinessException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.InventoryMapper;
import com.inventory.repository.AppUserRepository;
import com.inventory.repository.InventoryRepository;
import com.inventory.repository.StockAdjustmentRepository;
import com.inventory.security.CurrentUser;
import com.inventory.service.InventoryService.Movement;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static com.inventory.service.MasterDataSupport.trimToNull;

/** Manual stock corrections and reorder-level maintenance. */
@Service
public class StockAdjustmentService {

    private final InventoryRepository inventories;
    private final StockAdjustmentRepository adjustments;
    private final AppUserRepository users;
    private final InventoryService inventoryService;
    private final Clock clock;

    public StockAdjustmentService(InventoryRepository inventories, StockAdjustmentRepository adjustments,
                                  AppUserRepository users, InventoryService inventoryService, Clock clock) {
        this.inventories = inventories;
        this.adjustments = adjustments;
        this.users = users;
        this.inventoryService = inventoryService;
        this.clock = clock;
    }

    /**
     * Records the adjustment and moves the stock in ONE transaction: the stock row is locked, the
     * user's view of the quantity is checked, then the adjustment and its ledger entry are written.
     * If the movement is rejected (e.g. it would go below zero) neither is saved.
     */
    @Transactional
    public StockAdjustmentResponse adjust(StockAdjustmentRequest request) {
        if (request.adjustmentQuantity() == 0) {
            throw new BusinessException("Adjustment quantity must not be zero");
        }
        String notes = trimToNull(request.notes());
        if (request.reason() == AdjustmentReason.OTHER && notes == null) {
            throw new BusinessException("Notes are required when the reason is 'Other'");
        }

        Inventory stock = inventories.lockByProductId(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", request.productId()));
        if (stock.getQuantity() != request.expectedCurrentQuantity()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Stock of '" + stock.getProduct().getTitle()
                    + "' changed to " + stock.getQuantity() + " while you were entering the adjustment. "
                    + "Review the new quantity and try again");
        }
        // Checked before anything is written so the user gets the clear "insufficient stock" message.
        inventoryService.requireAvailable(stock, request.adjustmentQuantity());

        AppUser actor = users.getReferenceById(CurrentUser.requireId());
        StockAdjustment adjustment = adjustments.save(new StockAdjustment(stock.getProduct(), request.reason(),
                stock.getQuantity(), request.adjustmentQuantity(), notes, actor, Instant.now(clock)));

        StockTransaction tx = inventoryService.apply(new Movement(stock.getProduct().getId(), TransactionType.ADJUSTMENT,
                request.adjustmentQuantity(), ReferenceType.ADJUSTMENT, adjustment.getId(), ledgerNote(request.reason(), notes)));

        return InventoryMapper.toResponse(adjustment, tx.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentResponse> list(Long productId, Pageable pageable) {
        return PageResponse.of(adjustments.search(productId, pageable), a -> InventoryMapper.toResponse(a, null));
    }

    @Transactional
    public InventoryItem changeReorderLevel(Long productId, ReorderLevelRequest request) {
        Inventory stock = inventories.lockByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        stock.changeReorderLevel(request.reorderLevel(), Instant.now(clock));
        inventories.flush();
        return inventories.findDetailedByProductId(productId).map(InventoryMapper::toItem).orElseThrow();
    }

    private static String ledgerNote(AdjustmentReason reason, String notes) {
        String label = switch (reason) {
            case DAMAGED -> "Damaged";
            case LOST -> "Lost";
            case COUNT_CORRECTION -> "Physical count correction";
            case FOUND -> "Found";
            case OTHER -> "Other";
        };
        return notes == null ? label : label + ": " + notes;
    }
}
