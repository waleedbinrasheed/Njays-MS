package com.inventory.dto.purchase;

import com.inventory.dto.inventory.InventoryDtos.ProductRef;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record PurchaseLineRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1_000_000, message = "Quantity is too large") Integer quantity,
            @NotNull(message = "Unit cost is required")
            @DecimalMin(value = "0.00", message = "Unit cost cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Unit cost must have at most 2 decimals") BigDecimal unitCost,
            @DecimalMin(value = "0.00", message = "Discount % cannot be negative")
            @DecimalMax(value = "100.00", message = "Discount % cannot exceed 100")
            @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @DecimalMin(value = "0.00", message = "Tax % cannot be negative")
            @DecimalMax(value = "100.00", message = "Tax % cannot exceed 100")
            @Digits(integer = 3, fraction = 2) BigDecimal taxPercent) {
    }

    public record CreatePurchaseRequest(
            @NotNull(message = "Supplier is required") Long supplierId,
            // "Not in the future" is checked in the service using the shop's time zone.
            @NotNull(message = "Purchase date is required") LocalDate purchaseDate,
            @Size(max = 50) String supplierInvoiceNumber,
            @NotNull(message = "Payment status is required") PaymentStatus paymentStatus,
            @DecimalMin(value = "0.00", message = "Invoice discount cannot be negative")
            @Digits(integer = 12, fraction = 2) BigDecimal invoiceDiscount,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Add at least one item")
            @Size(max = 200, message = "A purchase can have at most 200 lines") List<@Valid PurchaseLineRequest> items) {
    }

    public record PaymentStatusRequest(@NotNull(message = "Payment status is required") PaymentStatus paymentStatus) {
    }

    public record CancelRequest(@NotBlank(message = "Give a reason for cancelling") @Size(max = 500) String reason) {
    }

    public record PurchaseLine(
            Long id,
            int lineNo,
            ProductRef product,
            int quantity,
            BigDecimal unitCost,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }

    public record PurchaseSummary(
            Long id,
            String purchaseNumber,
            LocalDate purchaseDate,
            Ref supplier,
            String supplierInvoiceNumber,
            BigDecimal total,
            PaymentStatus paymentStatus,
            DocumentStatus status,
            Ref createdBy,
            Instant createdAt) {
    }

    public record PurchaseDetail(
            Long id,
            String purchaseNumber,
            LocalDate purchaseDate,
            Ref supplier,
            String supplierInvoiceNumber,
            BigDecimal subtotal,
            BigDecimal invoiceDiscount,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal total,
            PaymentStatus paymentStatus,
            DocumentStatus status,
            String notes,
            List<PurchaseLine> items,
            Ref createdBy,
            Instant createdAt,
            Instant cancelledAt,
            Ref cancelledBy,
            String cancelReason,
            long version) {
    }
}
