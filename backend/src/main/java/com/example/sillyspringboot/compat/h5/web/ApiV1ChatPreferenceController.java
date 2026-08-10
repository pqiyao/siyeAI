package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ChatPreferenceService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/me/chat-preferences")
public class ApiV1ChatPreferenceController {
    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final H5ChatPreferenceService preferenceService;

    public ApiV1ChatPreferenceController(H5ClientUidAuthService h5Auth, AppTokenService tokenService,
                                         H5ChatPreferenceService preferenceService) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiV1Result<Map<String, Object>> get(@RequestParam String clientUid,
                                                 @RequestParam(required = false) Long characterId) {
        long userId = tokenService.validateAndLoadUser(h5Auth.requireAuthenticatedTokenForClientUid(clientUid)).getId();
        return ApiV1Result.ok(preferenceService.load(userId, characterId));
    }

    @PutMapping
    public ApiV1Result<Map<String, Object>> save(@RequestParam String clientUid,
                                                  @RequestParam(required = false) Long characterId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        long userId = tokenService.validateAndLoadUser(h5Auth.requireAuthenticatedTokenForClientUid(clientUid)).getId();
        return ApiV1Result.ok(preferenceService.save(userId, characterId, body));
    }
}
