package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.checkin.service.CheckinService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/checkin")
public class AdminJiugaiCheckinController {

    private final CheckinService checkinService;

    public AdminJiugaiCheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @GetMapping("/activity")
    @AdminPermitted("commerce:checkin:view")
    public Map<String, Object> activity() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", checkinService.adminGetActivity());
        return result;
    }

    @PutMapping("/activity")
    @AdminPermitted("commerce:checkin:edit")
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", checkinService.adminSaveActivity(body));
        return result;
    }

    @GetMapping("/claims/list")
    @AdminPermitted("commerce:checkin:view")
    public Map<String, Object> claims(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bizDate
    ) {
        return AdminAjaxResult.table(
                checkinService.countAdminClaims(keyword, bizDate),
                checkinService.listAdminClaims(keyword, bizDate, pageNum, pageSize)
        );
    }
}
