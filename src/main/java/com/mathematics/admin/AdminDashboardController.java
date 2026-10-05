package com.mathematics.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mathematics.guard.RequireAdmin;

@RestController
@RequireAdmin
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final DashboardService dashboard;

    public AdminDashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    public DashboardService.Dashboard get(@RequestParam(defaultValue = "14") int days) {
        return dashboard.load(days);
    }
}
