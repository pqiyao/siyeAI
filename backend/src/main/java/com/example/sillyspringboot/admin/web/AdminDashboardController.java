package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminDashboardService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/dashboard")
@AdminPermitted({
        "system:admin-user:view",
        "support:ticket:list",
        "content:character:view",
        "commerce:user:view",
        "ops:openrouter:view"
})
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String trendRange) {
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", dashboardService.overview(trendRange));
        return r;
    }
}
