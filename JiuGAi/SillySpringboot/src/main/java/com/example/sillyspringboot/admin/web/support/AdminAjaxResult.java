package com.example.sillyspringboot.admin.web.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdminAjaxResult {

    private AdminAjaxResult() {}

    public static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "操作成功");
        return m;
    }

    public static Map<String, Object> ok(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", msg);
        return m;
    }

    public static Map<String, Object> okData(Object data) {
        Map<String, Object> m = ok();
        m.put("data", data);
        return m;
    }

    public static Map<String, Object> table(long total, List<?> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "查询成功");
        m.put("rows", rows);
        m.put("total", total);
        return m;
    }

    public static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 500);
        m.put("msg", msg);
        return m;
    }

    public static Map<String, Object> unauthorized(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 401);
        m.put("msg", msg != null ? msg : "未认证");
        return m;
    }

    public static Map<String, Object> forbidden(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 403);
        m.put("msg", msg != null ? msg : "没有权限");
        return m;
    }
}
