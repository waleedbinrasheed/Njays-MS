package com.inventory.dto.sale;

import com.inventory.dto.inventory.InventoryDtos.ProductRef;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.master.MasterDataDtos.RepresentativeRef;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {
    }

    public record SaleLineRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1_000_000, message = "Quantity is too large") Integer quantity,
            @NotNull(message = "Unit price is required")
            @DecimalMin(value = "0.01", message = "Unit price must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Unit price must have at most 2 decimals") BigDecimal unitPrice,
            @DecimalMin(value = "0.00", message = "Discount % cannot be negative")
            @DecimalMax(value = "100.00", message = "Discount % cannot exceed 100")
            @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @DecimalMin(value = "0.00", message = "Tax % cannot be negative")
            @DecimalMax(value = "100.00", message = "Tax % cannot exceed 100")
            @Digits(integer = 3, fraction = 2) BigDecimal taxPercent) {
    }

    /**
     * The buyer is the representative (use the built-in "Counter Sales" one for counter sales).
     * {@code customerId} is optional and must be one of that representative's customers.
     */
    public record CreateSaleRequest(
            @NotNull(message = "Representative is required") Long representativeId,
            Long customerId,
            // "Not in the future" is checked in the service using the shop's time zone.
            @NotNull(message = "Sale date is required") LocalDate saleDate,
            @NotNull(message = "Payment status is required") PaymentStatus paymentStatus,
            @DecimalMin(value = "0.00", message = "Invoice discount cannot be negative")
            @Digits(integer = 12, fraction = 2) BigDecimal invoiceDiscount,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Add at least one item")
            @Size(max = 200, message = "An invoice can have at most 200 lines") List<@Valid SaleLineRequest> items) {
    }

    public record SaleLine(
            Long id,
            int lineNo,
            ProductRef product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
    }

    /** {@code representative} is null on invoices from before representatives existed; {@code customer} may be null on new ones. */
    public record SaleSummary(
            Long id,
            String invoiceNumber,
            LocalDate saleDate,
            RepresentativeRef representative,
            Ref customer,
            BigDecimal total,
            PaymentStatus paymentStatus,
            DocumentStatus status,
            Ref createdBy,
            Instant createdAt) {
    }

    public record SaleDetail(
            Long id,
            String invoiceNumber,
            LocalDate saleDate,
            RepresentativeRef representative,
            Ref customer,
            String customerPhone,
            String customerAddress,
            BigDecimal subtotal,
            BigDecimal invoiceDiscount,
            BigDecimal discountTotal,
            BigDecimal taxTotal,
            BigDecimal total,
            PaymentStatus paymentStatus,
            DocumentStatus status,
            String notes,
            List<SaleLine> items,
            Ref createdBy,
            Instant createdAt,
            Instant cancelledAt,
            Ref cancelledBy,
            String cancelReason,
            long version) {
    }
}
