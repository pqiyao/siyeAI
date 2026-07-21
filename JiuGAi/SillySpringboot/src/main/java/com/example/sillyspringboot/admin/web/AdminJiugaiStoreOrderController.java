package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.billing.service.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/store-order")
@AdminPermitted("commerce:order:view")
public class AdminJiugaiStoreOrderController {

    private final StoreService storeService;

    public AdminJiugaiStoreOrderController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return AdminAjaxResult.table(
                storeService.countAdminOrders(keyword, status),
                storeService.listAdminOrders(keyword, status, pageNum, pageSize)
        );
    }

    @GetMapping("/{orderNo}")
    public Map<String, Object> get(@PathVariable String orderNo) {
        Map<String, Object> row = storeService.getAdminOrder(orderNo);
        if (row == null) {
            return AdminAjaxResult.error("订单不存在");
        }
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", row);
        return result;
    }
}
