package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminDashboardService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.generation.dto.GenerationModelPricingAdminDto;
import com.example.sillyspringboot.ops.generation.service.GenerationModelPricingService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final GenerationModelPricingService generationModelPricingService;

    public AdminDashboardController(
            AdminDashboardService dashboardService,
            GenerationModelPricingService generationModelPricingService
    ) {
        this.dashboardService = dashboardService;
        this.generationModelPricingService = generationModelPricingService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String trendRange) {
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", dashboardService.overview(trendRange));
        return r;
    }

    @GetMapping("/model-pricing")
    public Map<String, Object> listModelPricing() {
        return AdminAjaxResult.okData(generationModelPricingService.listAll());
    }

    @PostMapping("/model-pricing")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveModelPricing(
            @RequestBody(required = false) GenerationModelPricingAdminDto body
    ) {
        try {
            Map<String, Object> result = AdminAjaxResult.ok("保存成功");
            result.put("data", generationModelPricingService.save(body));
            return result;
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/model-pricing/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteModelPricing(@PathVariable("id") Long id) {
        try {
            generationModelPricingService.delete(id);
            return AdminAjaxResult.ok("删除成功");
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }
}
