package com.example.sillyspringboot.admin.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminRuntimeSupportController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/noise/dashboard/state", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> noiseDashboardState() {
        return Map.of(
                "code", 200,
                "msg", "ok",
                "data", Map.of("online", true));
    }
}
