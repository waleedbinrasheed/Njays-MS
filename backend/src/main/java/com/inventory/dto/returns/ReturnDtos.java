package com.inventory.dto.returns;

import com.inventory.dto.inventory.InventoryDtos.ProductRef;
import com.inventory.dto.master.MasterDataDtos.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Shared by sales returns and purchase returns ("document" = the original invoice / purchase). */
public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record ReturnLineRequest(
            @NotNull(message = "Line is required") Long documentItemId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1_000_000, message = "Quantity is too large") Integer quantity) {
    }

    public record CreateReturnRequest(
            @NotNull(message = "Original document is required") Long documentId,
            @NotNull(message = "Return date is required") LocalDate returnDate,
            @NotBlank(message = "Give a reason for the return") @Size(max = 500) String reason,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Choose at least one item to return")
            @Size(max = 200) List<@Valid ReturnLineRequest> items) {
    }

    /** What can still be returned from one line of the original document. */
    public record ReturnableLine(
            Long documentItemId,
            int lineNo,
            ProductRef product,
            int originalQuantity,
            int returnedQuantity,
            int returnableQuantity,
            BigDecimal lineTotal,
            BigDecimal returnedAmount) {
    }

    public record ReturnLine(Long id, Long documentItemId, ProductRef product, int quantity, BigDecimal lineTotal) {
    }

    public record ReturnSummary(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            Long documentId,
            String documentNumber,
            Ref party,
            BigDecimal total,
            Ref createdBy,
            Instant createdAt) {
    }

    public record ReturnDetail(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            Long documentId,
            String documentNumber,
            LocalDate documentDate,
            Ref party,
            BigDecimal total,
            String reason,
            String notes,
            List<ReturnLine> items,
            Ref createdBy,
            Instant createdAt) {
    }
}
