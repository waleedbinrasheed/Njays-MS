package com.inventory.dto.report;

import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.ReferenceType;
import com.inventory.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** One page of report rows plus totals over ALL matching rows (not just this page). */
    public record ReportPage<T, S>(List<T> content, int page, int size, long totalElements, int totalPages, S summary) {
    }

    // --- Inventory / low stock ---

    public record InventoryRow(Long productId, String isbn, String title, String publisher, String category,
                               int quantity, int reorderLevel, BigDecimal purchasePrice, BigDecimal sellingPrice,
                               BigDecimal stockValue, boolean active) {
    }

    /** stockValue = quantity x purchase price (cost value of stock on hand). */
    public record InventorySummary(long products, long units, BigDecimal stockValue) {
    }

    public record LowStockRow(Long productId, String isbn, String title, String publisher, String category,
                              int quantity, int reorderLevel, int shortfall, BigDecimal purchasePrice) {
    }

    public record LowStockSummary(long products, long outOfStock, long unitsToReorder) {
    }

    // --- Sales / purchases ---

    /**
     * {@code party} is the buyer: the representative for sales (null on pre-representative invoices)
     * or the supplier for purchases. {@code customer} is the sale's optional customer.
     */
    public record DocumentRow(Long id, String number, LocalDate date, Long partyId, String party, String customer, int items,
                              BigDecimal total, PaymentStatus paymentStatus, DocumentStatus status, String createdBy) {
    }

    /** Totals of COMPLETED documents in the result (cancelled ones are listed but not counted). */
    public record DocumentSummary(long documents, BigDecimal total, BigDecimal paid, BigDecimal unpaid, BigDecimal partial) {
    }

    // --- Representatives ---

    /** {@code id} is null for the "Unassigned" row. owed = unpaid + partially paid invoice totals. */
    public record RepresentativeRow(Long id, String name, String area, boolean active, long customers, long invoices,
                                    BigDecimal salesTotal, BigDecimal returns, BigDecimal netSales, BigDecimal paid,
                                    BigDecimal owed, LocalDate lastSale) {
    }

    public record RepresentativeSummary(long rows, long representatives, long invoices, BigDecimal salesTotal,
                                        BigDecimal returns, BigDecimal netSales, BigDecimal owed) {
    }

    // --- Stock movement ---

    public record MovementRow(Long id, Instant date, Long productId, String product, String isbn, TransactionType type,
                              int quantity, int previousQuantity, int resultingQuantity, ReferenceType referenceType,
                              Long referenceId, String reference, String user, String notes) {
    }

    public record MovementSummary(long movements, long unitsIn, long unitsOut) {
    }

    // --- Dashboard ---

    public record Figure(long count, BigDecimal total) {
    }

    public record RecentDocument(Long id, String number, LocalDate date, String party, BigDecimal total,
                                 DocumentStatus status) {
    }

    public record RecentMovement(Long id, Instant date, String product, TransactionType type, int quantity, String user) {
    }

    /**
     * Sections the viewer may not see are null (e.g. purchases for a user without PURCHASE_VIEW).
     * Sales and purchase figures count COMPLETED documents by document date in the shop's time zone.
     */
    public record Dashboard(
            LocalDate today,
            Long totalProducts,
            Long totalQuantity,
            Long lowStockProducts,
            Long outOfStockProducts,
            Figure todaySales,
            Figure monthSales,
            Figure todayPurchases,
            Figure monthPurchases,
            List<LowStockRow> lowStock,
            List<RecentDocument> recentSales,
            List<RecentDocument> recentPurchases,
            List<RecentMovement> recentMovements) {
    }
}
