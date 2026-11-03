package com.inventory.dto.report;

import com.inventory.entity.OrderStatus;
import com.inventory.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** One order in the orders report. Cancelled orders are listed but not counted in the totals. */
    public record OrderReportRow(
            Long id, String orderNumber, LocalDate orderDate, String customerName, OrderStatus status,
            LocalDate dueDate, BigDecimal total, BigDecimal paid, BigDecimal balance, PaymentStatus paymentStatus) {
    }

    public record OrderReportTotals(long orders, BigDecimal total, BigDecimal paid, BigDecimal balance) {
    }

    public record OrderReport(List<OrderReportRow> rows, OrderReportTotals totals) {
    }

    public record FabricStockRow(
            Long id, String code, String name, String colour, BigDecimal quantityOnHand,
            BigDecimal reorderLevel, boolean lowStock, BigDecimal costPerMetre, BigDecimal costValue) {
    }

    public record FabricStockTotals(long fabrics, BigDecimal quantityOnHand, BigDecimal costValue) {
    }

    public record FabricStockReport(List<FabricStockRow> rows, FabricStockTotals totals) {
    }

    public record RecentOrder(Long id, String orderNumber, String customerName, OrderStatus status, LocalDate dueDate) {
    }

    /**
     * Sections are null when the viewer lacks the permission for them: order figures need ORDER_VIEW,
     * {@code lowStockFabrics} needs INVENTORY_VIEW.
     */
    public record Dashboard(
            LocalDate today,
            Long dueToday,
            Long overdue,
            Long readyForDelivery,
            Map<OrderStatus, Long> inProgress,
            BigDecimal outstandingBalance,
            Long ordersToday,
            List<RecentOrder> recentOrders,
            Long lowStockFabrics) {
    }
}
