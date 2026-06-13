package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernAiProviderController {

    private final H5UserAiProviderService userAiProviderService;

    public ApiV1TavernAiProviderController(H5UserAiProviderService userAiProviderService) {
        this.userAiProviderService = userAiProviderService;
    }

    @GetMapping("/ai-provider")
    public ApiV1Result<H5UserAiProviderService.UserAiProviderView> get(
            @RequestParam("clientUid") String clientUid
    ) {
        return ApiV1Result.ok(userAiProviderService.getView(clientUid));
    }

    @PutMapping("/ai-provider")
    public ApiV1Result<H5UserAiProviderService.UserAiProviderView> save(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiV1Result.ok(userAiProviderService.save(clientUid, body));
    }

    @PostMapping("/ai-provider/test")
    public ApiV1Result<H5UserAiProviderService.UserAiProviderTestResult> test(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiV1Result.ok(userAiProviderService.testConnection(clientUid, body));
    }

    @PostMapping("/ai-provider/models")
    public ApiV1Result<H5UserAiProviderService.UserAiProviderModelsResult> models(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiV1Result.ok(userAiProviderService.listModels(clientUid, body));
    }
}
