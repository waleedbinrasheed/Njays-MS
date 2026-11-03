package com.inventory.dto.inventory;

import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.AdjustmentReason;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Fabric stock. All quantities are metres with up to 3 decimal places. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    public enum StockLevel {
        OUT_OF_STOCK,
        LOW,
        OK
    }

    public record ProductRef(Long id, String code, String name) {
    }

    public record InventoryItem(
            Long productId,
            String code,
            String name,
            String colour,
            String material,
            BigDecimal quantity,
            BigDecimal reorderLevel,
            StockLevel stockLevel,
            boolean active,
            Instant updatedAt) {
    }

    public record InventorySummary(long totalFabrics, BigDecimal totalMetres, long lowStockFabrics,
                                   long outOfStockFabrics) {
    }

    public record ReorderLevelRequest(
            @NotNull(message = "Reorder level is required")
            @Digits(integer = 9, fraction = 3, message = "Reorder level must be a number of metres with at most 3 decimals")
            BigDecimal reorderLevel) {
    }

    public record StockTransactionResponse(
            Long id,
            Instant createdAt,
            ProductRef product,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal previousQuantity,
            BigDecimal resultingQuantity,
            ReferenceType referenceType,
            Long referenceId,
            Ref user,
            String notes) {
    }

    /**
     * {@code expectedCurrentQuantity} is the stock the user saw when entering the adjustment. If the
     * stock moved in the meantime (e.g. an order), the adjustment is rejected rather than applied to a
     * number the user never saw. Stock-in uses reason STOCK_IN with a positive quantity.
     */
    public record StockAdjustmentRequest(
            @NotNull(message = "Fabric is required") Long productId,
            @NotNull(message = "Adjustment quantity is required")
            @Digits(integer = 9, fraction = 3, message = "Quantity must be metres with at most 3 decimals")
            BigDecimal adjustmentQuantity,
            @NotNull(message = "Current quantity is required")
            @Digits(integer = 9, fraction = 3, message = "Quantity must be metres with at most 3 decimals")
            BigDecimal expectedCurrentQuantity,
            @NotNull(message = "Reason is required") AdjustmentReason reason,
            @Size(max = 500) String notes) {
    }

    public record StockAdjustmentResponse(
            Long id,
            ProductRef product,
            AdjustmentReason reason,
            BigDecimal previousQuantity,
            BigDecimal adjustmentQuantity,
            BigDecimal resultingQuantity,
            String notes,
            Ref user,
            Instant createdAt,
            Long stockTransactionId) {
    }
}
