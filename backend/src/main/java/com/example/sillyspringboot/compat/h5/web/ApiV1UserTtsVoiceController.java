package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
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

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern/user-voices")
public class ApiV1UserTtsVoiceController {

    private final H5EntitlementService entitlementService;
    private final UserTtsVoiceService voiceService;
    private final AppFeatureSettingsService featureSettingsService;
    private final ChatAudioSpeechService speechService;
    private final MediaConcurrencyGate mediaGate;

    public ApiV1UserTtsVoiceController(
            H5EntitlementService entitlementService,
            UserTtsVoiceService voiceService,
            AppFeatureSettingsService featureSettingsService,
            ChatAudioSpeechService speechService,
            MediaConcurrencyGate mediaGate
    ) {
        this.entitlementService = entitlementService;
        this.voiceService = voiceService;
        this.featureSettingsService = featureSettingsService;
        this.speechService = speechService;
        this.mediaGate = mediaGate;
    }

    @GetMapping
    public ApiV1Result<Map<String, Object>> overview(@RequestParam("clientUid") String clientUid) {
        featureSettingsService.ensureVoiceFeatureEnabled();
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
        featureSettingsService.ensureVoiceFeatureEnabled();
        return ApiV1Result.ok(voiceService.create(
                user(clientUid).getId(), requestId, displayName, sampleText, intValue(durationMs), file));
    }

    @GetMapping("/provider/status")
    public ApiV1Result<Map<String, Object>> providerStatus(@RequestParam("clientUid") String clientUid) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        return ApiV1Result.ok(voiceService.providerStatus(user(clientUid).getId()));
    }

    @GetMapping("/provider/voices")
    public ApiV1Result<List<Map<String, Object>>> providerVoices(@RequestParam("clientUid") String clientUid) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        return ApiV1Result.ok(voiceService.providerVoices(user(clientUid).getId()));
    }

    @PostMapping("/provider/import")
    public ApiV1Result<Map<String, Object>> importProviderVoice(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        Map<String, Object> safe = body == null ? Map.of() : body;
        return ApiV1Result.ok(voiceService.importProviderVoice(
                user(clientUid).getId(), stringValue(safe.get("requestId")), stringValue(safe.get("voiceUri"))));
    }

    @PutMapping("/{voiceId}")
    public ApiV1Result<Map<String, Object>> rename(
            @RequestParam("clientUid") String clientUid,
            @PathVariable long voiceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        return ApiV1Result.ok(voiceService.rename(
                user(clientUid).getId(), voiceId, body == null ? "" : String.valueOf(body.getOrDefault("displayName", ""))));
    }

    @DeleteMapping("/{voiceId}")
    public ApiV1Result<Void> remove(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(defaultValue = "false") boolean deleteProvider,
            @PathVariable long voiceId
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        long userId = user(clientUid).getId();
        if (deleteProvider) voiceService.deleteProviderResource(userId, voiceId);
        voiceService.remove(userId, voiceId);
        return ApiV1Result.ok(null);
    }

    @PostMapping("/{voiceId}/preview")
    public ApiV1Result<Map<String, Object>> preview(
            @RequestParam("clientUid") String clientUid,
            @PathVariable long voiceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        Map<String, Object> safe = body == null ? Map.of() : body;
        String requestId = requestId(safe.get("requestId"));
        String text = previewText(safe.get("text"));
        long userId = user(clientUid).getId();
        try (MediaConcurrencyGate.Lease ignored = mediaGate.acquire(
                MediaConcurrencyGate.Capability.TTS, userId, requestId)) {
            ChatAudioSpeechService.AudioSpeechResult result =
                    speechService.synthesizePrivateUserVoice(userId, text, voiceId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("audioDataUrl", "data:" + result.mimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(result.audioBytes()));
            data.put("mimeType", result.mimeType());
            data.put("modelName", result.modelName());
            data.put("voiceName", result.voiceName());
            return ApiV1Result.ok(data);
        }
    }

    @GetMapping("/binding")
    public ApiV1Result<Map<String, Object>> binding(
            @RequestParam("clientUid") String clientUid,
            @RequestParam String scopeType,
            @RequestParam(defaultValue = "0") long characterId,
            @RequestParam(defaultValue = "0") long memberId
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
        return ApiV1Result.ok(voiceService.getBinding(
                user(clientUid).getId(), scopeType, characterId, memberId));
    }

    @PutMapping("/binding")
    public ApiV1Result<Map<String, Object>> saveBinding(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        featureSettingsService.ensureVoiceFeatureEnabled();
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

    private static String requestId(Object value) {
        String text = stringValue(value).trim();
        if (!text.matches("[A-Za-z0-9_-]{12,64}")) {
            throw new com.example.sillyspringboot.shared.error.BusinessException(
                    com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "试听请求标识无效");
        }
        return text;
    }

    private static String previewText(Object value) {
        String text = stringValue(value).replaceAll("\\s+", " ").trim();
        if (text.isBlank()) {
            throw new com.example.sillyspringboot.shared.error.BusinessException(
                    com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "请输入试听文字");
        }
        if (text.length() > 160) {
            throw new com.example.sillyspringboot.shared.error.BusinessException(
                    com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "试听文字不能超过 160 个字符");
        }
        return text;
    }
}
