package com.inventory.dto.inventory;

import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.AdjustmentReason;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public enum StockLevel {
        OUT_OF_STOCK,
        LOW,
        OK
    }

    public record ProductRef(Long id, String title, String isbn) {
    }

    public record InventoryItem(
            Long productId,
            String isbn,
            String title,
            String author,
            Ref publisher,
            Ref category,
            int quantity,
            int reorderLevel,
            StockLevel stockLevel,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            boolean active,
            Instant updatedAt) {
    }

    public record InventorySummary(long totalProducts, long totalQuantity, long lowStockProducts, long outOfStockProducts) {
    }

    public record ReorderLevelRequest(
            @NotNull(message = "Reorder level is required")
            @Min(value = 0, message = "Reorder level cannot be negative")
            @Max(value = 1_000_000, message = "Reorder level is too large") Integer reorderLevel) {
    }

    public record StockTransactionResponse(
            Long id,
            Instant createdAt,
            ProductRef product,
            TransactionType type,
            int quantity,
            int previousQuantity,
            int resultingQuantity,
            ReferenceType referenceType,
            Long referenceId,
            Ref user,
            String notes) {
    }

    /**
     * {@code expectedCurrentQuantity} is the stock the user saw when entering the adjustment. If the
     * stock moved in the meantime (e.g. a sale), the adjustment is rejected rather than applied to a
     * number the user never saw.
     */
    public record StockAdjustmentRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Adjustment quantity is required")
            @Min(value = -1_000_000, message = "Adjustment is too large")
            @Max(value = 1_000_000, message = "Adjustment is too large") Integer adjustmentQuantity,
            @NotNull(message = "Current quantity is required") @Min(0) Integer expectedCurrentQuantity,
            @NotNull(message = "Reason is required") AdjustmentReason reason,
            @Size(max = 500) String notes) {
    }

    public record StockAdjustmentResponse(
            Long id,
            ProductRef product,
            AdjustmentReason reason,
            int previousQuantity,
            int adjustmentQuantity,
            int resultingQuantity,
            String notes,
            Ref user,
            Instant createdAt,
            Long stockTransactionId) {
    }
}
