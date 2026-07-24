package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ops.checkin.service.CheckinService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkin")
public class ApiV1CheckinController {

    private final CheckinService checkinService;

    public ApiV1CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @GetMapping("/status")
    public ApiV1Result<Map<String, Object>> status(@RequestParam("clientUid") String clientUid) {
        return ApiV1Result.ok(checkinService.status(clientUid));
    }

    @PostMapping("/claim")
    public ApiV1Result<Map<String, Object>> claim(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : stringValue(payload.get("clientUid"));
        return ApiV1Result.ok(checkinService.claim(clientUid));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
