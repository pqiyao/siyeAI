package com.example.sillyspringboot.chat.web;

import com.example.sillyspringboot.chat.service.AppChatFrontendBridgeService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/st-frontend-bridge")
public class StFrontendBridgeController {

    private static final String TOKEN_HEADER = "X-ST-H5-Bridge-Token";

    private final AppChatFrontendBridgeService bridgeService;

    public StFrontendBridgeController(AppChatFrontendBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    @GetMapping("/next")
    public AppChatFrontendBridgeService.BridgeJobPayload next(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestParam(name = "workerId", required = false) String workerId,
            @RequestParam(name = "waitMs", required = false, defaultValue = "20000") long waitMs
    ) {
        requireToken(token);
        return bridgeService.pollNext(workerId, waitMs);
    }

    @PostMapping("/complete")
    public Map<String, Object> complete(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestBody AppChatFrontendBridgeService.BridgeCompletion completion
    ) {
        requireToken(token);
        boolean accepted = completion != null && bridgeService.complete(completion.jobId(), completion);
        return Map.of("ok", accepted);
    }

    @PostMapping("/fail")
    public Map<String, Object> fail(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestBody Map<String, Object> body
    ) {
        requireToken(token);
        String jobId = body == null ? "" : String.valueOf(body.getOrDefault("jobId", ""));
        String error = body == null ? "" : String.valueOf(body.getOrDefault("error", ""));
        boolean accepted = bridgeService.fail(jobId, error);
        return Map.of("ok", accepted);
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        requireToken(token);
        String workerId = body == null ? "" : String.valueOf(body.getOrDefault("workerId", ""));
        bridgeService.heartbeat(workerId);
        return Map.of("ok", true, "status", bridgeService.status());
    }

    @GetMapping("/job/{jobId}")
    public Map<String, Object> job(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            @PathVariable String jobId
    ) {
        requireToken(token);
        return Map.of("jobId", jobId, "cancelled", bridgeService.isJobCancelled(jobId));
    }

    @GetMapping("/status")
    public AppChatFrontendBridgeService.BridgeStatus status(
            @RequestHeader(name = TOKEN_HEADER, required = false) String token
    ) {
        requireToken(token);
        return bridgeService.status();
    }

    private void requireToken(String token) {
        if (!bridgeService.validToken(token)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "frontend bridge token invalid");
        }
    }
}
