package com.inventory.dto.sample;

import com.inventory.dto.inventory.InventoryDtos.ProductRef;
import com.inventory.dto.master.MasterDataDtos.Ref;
import com.inventory.dto.master.MasterDataDtos.RepresentativeRef;
import com.inventory.entity.SampleStatus;
import jakarta.validation.Valid;
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

/** Sample issues ("SAMPLE SALE" documents) and sample returns. Amount = quantity x sample price; no discount or tax. */
public final class SampleDtos {

    private SampleDtos() {
    }

    // --- Issue ---

    public record SampleLineRequest(
            @NotNull(message = "Book is required") Long productId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1_000_000, message = "Quantity is too large") Integer quantity,
            // "Not above the selling price" is checked in the service against the book.
            @NotNull(message = "Sample price is required")
            @DecimalMin(value = "0.00", message = "Sample price cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Sample price must have at most 2 decimals") BigDecimal unitPrice) {
    }

    public record CreateSampleRequest(
            @NotNull(message = "Representative is required") Long representativeId,
            // "Not in the future" is checked in the service using the shop's time zone.
            @NotNull(message = "Issue date is required") LocalDate issueDate,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Add at least one book")
            @Size(max = 200, message = "A sample can have at most 200 lines") List<@Valid SampleLineRequest> items) {
    }

    /** One issued book with how much has come back and how much is still out. */
    public record SampleLine(
            Long id,
            int lineNo,
            ProductRef product,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            int returnedQuantity,
            BigDecimal returnedAmount,
            int outstandingQuantity,
            BigDecimal outstandingAmount) {
    }

    public record SampleSummary(
            Long id,
            String sampleNumber,
            LocalDate issueDate,
            RepresentativeRef representative,
            int totalQuantity,
            BigDecimal total,
            int returnedQuantity,
            BigDecimal returnedTotal,
            int outstandingQuantity,
            BigDecimal outstandingTotal,
            SampleStatus status,
            Ref createdBy,
            Instant createdAt) {
    }

    /** A return recorded against a sample, as listed on the sample. */
    public record SampleReturnRef(Long id, String returnNumber, LocalDate returnDate, int totalQuantity, BigDecimal total) {
    }

    public record SampleDetail(
            Long id,
            String sampleNumber,
            LocalDate issueDate,
            RepresentativeRef representative,
            String representativeAddress,
            int totalQuantity,
            BigDecimal total,
            int returnedQuantity,
            BigDecimal returnedTotal,
            int outstandingQuantity,
            BigDecimal outstandingTotal,
            SampleStatus status,
            String notes,
            List<SampleLine> items,
            List<SampleReturnRef> returns,
            Ref createdBy,
            Instant createdAt,
            Instant cancelledAt,
            Ref cancelledBy,
            String cancelReason) {
    }

    // --- Return ---

    public record SampleReturnLineRequest(
            @NotNull(message = "Line is required") Long sampleItemId,
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 1_000_000, message = "Quantity is too large") Integer quantity) {
    }

    public record CreateSampleReturnRequest(
            @NotNull(message = "Sample is required") Long sampleId,
            @NotNull(message = "Return date is required") LocalDate returnDate,
            @Size(max = 500) String notes,
            @NotEmpty(message = "Choose at least one book to return")
            @Size(max = 200) List<@Valid SampleReturnLineRequest> items) {
    }

    public record SampleReturnLine(Long id, Long sampleItemId, ProductRef product, int quantity, BigDecimal unitPrice,
                                   BigDecimal lineTotal) {
    }

    public record SampleReturnSummary(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            Long sampleId,
            String sampleNumber,
            Ref representative,
            int totalQuantity,
            BigDecimal total,
            Ref createdBy,
            Instant createdAt) {
    }

    public record SampleReturnDetail(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            Long sampleId,
            String sampleNumber,
            LocalDate issueDate,
            RepresentativeRef representative,
            int totalQuantity,
            BigDecimal total,
            String notes,
            List<SampleReturnLine> items,
            Ref createdBy,
            Instant createdAt) {
    }

    // --- Sample report ---

    /** One issued book line. Cancelled samples are listed (outstanding 0) but left out of report totals. */
    public record SampleLineRow(
            Long sampleId,
            Long itemId,
            String sampleNumber,
            LocalDate issueDate,
            Long representativeId,
            String representative,
            Long productId,
            String title,
            String isbn,
            int issuedQuantity,
            BigDecimal unitPrice,
            BigDecimal issuedAmount,
            int returnedQuantity,
            BigDecimal returnedAmount,
            int outstandingQuantity,
            BigDecimal outstandingAmount,
            SampleStatus status) {
    }

    /** Per representative, over their non-cancelled samples. */
    public record SampleRepresentativeRow(
            Long id,
            String name,
            String area,
            boolean active,
            long samples,
            long issuedQuantity,
            BigDecimal issuedAmount,
            long returnedQuantity,
            BigDecimal returnedAmount,
            long outstandingQuantity,
            BigDecimal outstandingAmount,
            LocalDate lastIssue) {
    }

    /** Totals over every matching row (cancelled samples excluded). */
    public record SampleReportSummary(
            long rows,
            long samples,
            long issuedQuantity,
            BigDecimal issuedAmount,
            long returnedQuantity,
            BigDecimal returnedAmount,
            long outstandingQuantity,
            BigDecimal outstandingAmount) {
    }
}
