package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern/user-voices")
public class ApiV1UserTtsVoiceController {

    private final H5EntitlementService entitlementService;
    private final UserTtsVoiceService voiceService;

    public ApiV1UserTtsVoiceController(
            H5EntitlementService entitlementService,
            UserTtsVoiceService voiceService
    ) {
        this.entitlementService = entitlementService;
        this.voiceService = voiceService;
    }

    @GetMapping
    public ApiV1Result<Map<String, Object>> overview(@RequestParam("clientUid") String clientUid) {
        return ApiV1Result.ok(voiceService.overview(user(clientUid).getId()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiV1Result<Map<String, Object>> create(
            @RequestParam("clientUid") String clientUid,
            @RequestPart("requestId") String requestId,
            @RequestPart("displayName") String displayName,
            @RequestPart("sampleText") String sampleText,
            @RequestPart("durationMs") String durationMs,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiV1Result.ok(voiceService.create(
                user(clientUid).getId(), requestId, displayName, sampleText, intValue(durationMs), file));
    }

    @PutMapping("/{voiceId}")
    public ApiV1Result<Map<String, Object>> rename(
            @RequestParam("clientUid") String clientUid,
            @PathVariable long voiceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiV1Result.ok(voiceService.rename(
                user(clientUid).getId(), voiceId, body == null ? "" : String.valueOf(body.getOrDefault("displayName", ""))));
    }

    @DeleteMapping("/{voiceId}")
    public ApiV1Result<Void> remove(
            @RequestParam("clientUid") String clientUid,
            @PathVariable long voiceId
    ) {
        voiceService.remove(user(clientUid).getId(), voiceId);
        return ApiV1Result.ok(null);
    }

    @GetMapping("/binding")
    public ApiV1Result<Map<String, Object>> binding(
            @RequestParam("clientUid") String clientUid,
            @RequestParam String scopeType,
            @RequestParam(defaultValue = "0") long characterId,
            @RequestParam(defaultValue = "0") long memberId
    ) {
        return ApiV1Result.ok(voiceService.getBinding(
                user(clientUid).getId(), scopeType, characterId, memberId));
    }

    @PutMapping("/binding")
    public ApiV1Result<Map<String, Object>> saveBinding(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        return ApiV1Result.ok(voiceService.saveBinding(
                user(clientUid).getId(),
                stringValue(safe.get("scopeType")),
                longValue(safe.get("characterId")),
                longValue(safe.get("memberId")),
                nullableLong(safe.get("voiceId"))));
    }

    private AppUser user(String clientUid) {
        return entitlementService.resolveUser(clientUid);
    }

    private static int intValue(Object value) {
        try { return Integer.parseInt(String.valueOf(value).trim()); }
        catch (Exception ignored) { return 0; }
    }

    private static long longValue(Object value) {
        Long result = nullableLong(value);
        return result == null ? 0L : result;
    }

    private static Long nullableLong(Object value) {
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value).trim()); }
        catch (Exception ignored) { return null; }
    }

    private static String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
}
