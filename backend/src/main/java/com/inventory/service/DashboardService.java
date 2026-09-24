package com.inventory.service;

import com.inventory.dto.inventory.InventoryDtos.InventorySummary;
import com.inventory.dto.report.ReportDtos.Dashboard;
import com.inventory.dto.report.ReportDtos.Figure;
import com.inventory.dto.report.ReportDtos.LowStockRow;
import com.inventory.dto.report.ReportDtos.RecentDocument;
import com.inventory.dto.report.ReportDtos.RecentMovement;
import com.inventory.repository.ReportRepository;
import com.inventory.repository.ReportRepository.DocumentKind;
import com.inventory.security.AppUserPrincipal;
import com.inventory.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * Figures calculated live from the database. Each section is included only if the viewer holds the
 * matching permission, so the dashboard never leaks data their role cannot otherwise see.
 */
@Service
public class DashboardService {

    private static final int RECENT = 5;
    private static final int MOVEMENTS = 8;

    private final ReportRepository reports;
    private final StockQueryService stock;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(ReportRepository reports, StockQueryService stock, Clock clock, ZoneId businessZone) {
        this.reports = reports;
        this.stock = stock;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public Dashboard load() {
        Set<String> can = CurrentUser.principal().map(AppUserPrincipal::getPermissions).orElse(Set.of());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate monthStart = today.withDayOfMonth(1);

        boolean inventory = can.contains("INVENTORY_VIEW");
        boolean sales = can.contains("SALE_VIEW");
        boolean purchases = can.contains("PURCHASE_VIEW");

        InventorySummary inv = inventory ? stock.summary() : null;
        Figure todaySales = sales ? reports.documentFigure(DocumentKind.SALE, today, today) : null;
        Figure monthSales = sales ? reports.documentFigure(DocumentKind.SALE, monthStart, today) : null;
        Figure todayPurchases = purchases ? reports.documentFigure(DocumentKind.PURCHASE, today, today) : null;
        Figure monthPurchases = purchases ? reports.documentFigure(DocumentKind.PURCHASE, monthStart, today) : null;
        List<LowStockRow> lowStock = inventory ? reports.topLowStock(RECENT + 3) : null;
        List<RecentDocument> recentSales = sales ? reports.recentDocuments(DocumentKind.SALE, RECENT) : null;
        List<RecentDocument> recentPurchases = purchases ? reports.recentDocuments(DocumentKind.PURCHASE, RECENT) : null;
        List<RecentMovement> movements = inventory ? reports.recentMovements(MOVEMENTS) : null;

        return new Dashboard(today,
                inv == null ? null : inv.totalProducts(),
                inv == null ? null : inv.totalQuantity(),
                inv == null ? null : inv.lowStockProducts(),
                inv == null ? null : inv.outOfStockProducts(),
                todaySales, monthSales, todayPurchases, monthPurchases,
                lowStock, recentSales, recentPurchases, movements);
    }
}
