package com.inventory.controller;

import com.inventory.dto.report.ReportDtos.Dashboard;
import com.inventory.dto.report.ReportDtos.DocumentRow;
import com.inventory.dto.report.ReportDtos.DocumentSummary;
import com.inventory.dto.report.ReportDtos.InventoryRow;
import com.inventory.dto.report.ReportDtos.InventorySummary;
import com.inventory.dto.report.ReportDtos.LowStockRow;
import com.inventory.dto.report.ReportDtos.LowStockSummary;
import com.inventory.dto.report.ReportDtos.MovementRow;
import com.inventory.dto.report.ReportDtos.MovementSummary;
import com.inventory.dto.report.ReportDtos.ReportPage;
import com.inventory.entity.DocumentStatus;
import com.inventory.entity.PaymentStatus;
import com.inventory.entity.TransactionType;
import com.inventory.repository.InventorySpecifications.StockStatus;
import com.inventory.repository.ReportRepository.DocumentKind;
import com.inventory.service.DashboardService;
import com.inventory.service.ReportService;
import com.inventory.service.ReportService.DocumentFilter;
import com.inventory.service.ReportService.InventoryFilter;
import com.inventory.service.ReportService.MovementFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ReportController {

    private final ReportService reports;
    private final DashboardService dashboard;

    public ReportController(ReportService reports, DashboardService dashboard) {
        this.reports = reports;
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public Dashboard dashboard() {
        return dashboard.load();
    }

    @GetMapping("/api/reports/inventory")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportPage<InventoryRow, InventorySummary> inventory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long publisherId,
            @RequestParam(required = false) StockStatus stock,
            @RequestParam(required = false, defaultValue = "true") Boolean active,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.inventory(new InventoryFilter(search, productId, categoryId, publisherId, stock, active), pageable);
    }

    @GetMapping("/api/reports/low-stock")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportPage<LowStockRow, LowStockSummary> lowStock(@RequestParam(required = false) Long categoryId,
                                                             @RequestParam(required = false) Long publisherId,
                                                             @PageableDefault(size = 50) Pageable pageable) {
        return reports.lowStock(categoryId, publisherId, pageable);
    }

    @GetMapping("/api/reports/sales")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportPage<DocumentRow, DocumentSummary> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.documents(DocumentKind.SALE,
                new DocumentFilter(from, to, customerId, productId, paymentStatus, status), pageable);
    }

    @GetMapping("/api/reports/purchases")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportPage<DocumentRow, DocumentSummary> purchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) DocumentStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.documents(DocumentKind.PURCHASE,
                new DocumentFilter(from, to, supplierId, productId, paymentStatus, status), pageable);
    }

    @GetMapping("/api/reports/stock-movement")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ReportPage<MovementRow, MovementSummary> stockMovement(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50) Pageable pageable) {
        return reports.movements(new MovementFilter(productId, type, from, to), pageable);
    }
}
