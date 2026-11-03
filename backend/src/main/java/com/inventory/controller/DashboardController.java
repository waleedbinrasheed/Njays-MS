package com.inventory.controller;

import com.inventory.dto.report.ReportDtos.Dashboard;
import com.inventory.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public Dashboard dashboard() {
        return dashboard.load();
    }
}
