package com.inventory.controller;

import com.inventory.dto.report.ReportDtos.FabricStockReport;
import com.inventory.dto.report.ReportDtos.OrderReport;
import com.inventory.entity.OrderStatus;
import com.inventory.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public OrderReport orders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                              @RequestParam(required = false) OrderStatus status,
                              @RequestParam(required = false) Long customerId) {
        return reports.orders(from, to, status, customerId);
    }

    @GetMapping("/fabric-stock")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public FabricStockReport fabricStock(@RequestParam(required = false) String search,
                                         @RequestParam(defaultValue = "false") boolean lowStockOnly) {
        return reports.fabricStock(search, lowStockOnly);
    }
}
