package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.service.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/store-product")
@AdminPermitted("commerce:product:view")
public class AdminJiugaiStoreProductController {

    private final StoreService storeService;

    public AdminJiugaiStoreProductController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productType
    ) {
        return AdminAjaxResult.table(
                storeService.countAdminProducts(keyword, productType),
                storeService.listAdminProducts(keyword, productType, pageNum, pageSize)
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppStoreProduct row = storeService.getProduct(id);
        if (row == null) {
            return AdminAjaxResult.error("商品不存在");
        }
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", row);
        return result;
    }

    @PostMapping
    @AdminPermitted("commerce:product:edit")
    public Map<String, Object> add(@RequestBody AppStoreProduct body) {
        AppStoreProduct saved = storeService.saveProduct(body);
        Map<String, Object> result = AdminAjaxResult.ok("新增成功");
        result.put("data", saved);
        return result;
    }

    @PutMapping
    @AdminPermitted("commerce:product:edit")
    public Map<String, Object> update(@RequestBody AppStoreProduct body) {
        if (body == null || body.getId() == null) {
            return AdminAjaxResult.error("缺少商品 id");
        }
        AppStoreProduct saved = storeService.saveProduct(body);
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", saved);
        return result;
    }
}
