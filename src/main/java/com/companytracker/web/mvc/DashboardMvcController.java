package com.companytracker.web.mvc;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardMvcController {

    private final DashboardService dashboardService;

    public DashboardMvcController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("statusCounts", dashboardService.statusCounts());
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("upcoming", dashboardService.upcoming(14));
        return "dashboard";
    }
}
