package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceInstance;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceInstanceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.BoundedHttpBodyHandlers;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TtsVoiceProvisionService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_PROVISION_TIMEOUT_SECONDS = 90;
    private static final int REFERENCE_AUDIO_TIMEOUT_SECONDS = 30;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int MAX_REFERENCE_AUDIO_BYTES = 8 * 1024 * 1024;
    private static final int MAX_VOICE_API_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final long OFFICIAL_TEMPLATE_OWNER_ID = 0L;
    private static final Object[] TEMPLATE_PROVISION_LOCKS = createProvisionLocks(256);

    public record TtsRuntimeContext(
            boolean customModeActive,
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName
    ) {
        public boolean providerMatches(String expectedProviderSource) {
            return blank(providerSource).equalsIgnoreCase(blank(expectedProviderSource));
        }

        public boolean hasApiKey() {
            return StringUtils.hasText(blank(apiKey));
        }

        public String effectiveModelName(String fallbackModelName) {
            String model = blank(modelName);
            return StringUtils.hasText(model) ? model : blank(fallbackModelName);
        }
    }

    public record ResolvedVoice(
            String voiceUri,
            String modelName,
            String templateCode,
            String templateDisplayName
    ) {
    }

    public record ProvisionedUserVoice(String voiceUri, String modelName, String configFingerprint) {
    }

    public record ProviderAccount(String balance, String chargeBalance, String totalBalance) {
    }

    public record ProviderVoice(String voiceUri, String displayName, String modelName, String sampleText) {
    }

    private final TtsVoiceTemplateService templateService;
    private final AppUserTtsVoiceInstanceMapper instanceMapper;
    private final H5UploadService uploadService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TtsVoiceProvisionService(
            TtsVoiceTemplateService templateService,
            AppUserTtsVoiceInstanceMapper instanceMapper,
            H5UploadService uploadService,
            ObjectMapper objectMapper
    ) {
        this.templateService = templateService;
        this.instanceMapper = instanceMapper;
        this.uploadService = uploadService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public ResolvedVoice resolveVoiceForUser(long userId, String templateCode, TtsRuntimeContext runtimeContext) {
        long deadlineNanos = System.nanoTime() + DEFAULT_PROVISION_TIMEOUT_SECONDS * NANOS_PER_SECOND;
        return resolveVoiceForUser(userId, templateCode, runtimeContext, deadlineNanos);
    }

    public ResolvedVoice resolveVoiceForUser(
            long userId,
            String templateCode,
            TtsRuntimeContext runtimeContext,
            long deadlineNanos
    ) {
        requireRemainingSeconds(deadlineNanos);
        AppTtsVoiceTemplate template = templateService.findEnabledTemplate(templateCode);
        if (template == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前选择的音色模板已失效");
        }
        if (runtimeContext == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前 TTS 线路尚未就绪");
        }
        if (!runtimeContext.providerMatches(blank(template.getProviderSource()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前音色与 TTS 线路不兼容");
        }
        if (!runtimeContext.hasApiKey()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    runtimeContext.customModeActive() ? "请先填写当前 TTS 的 API Key" : "官方 TTS 线路尚未配置可用凭证");
        }
        String templateModelName = blank(template.getTtsModelName());
        String runtimeModelName = runtimeContext.effectiveModelName("");
        if (!runtimeContext.customModeActive()
                && StringUtils.hasText(templateModelName)
                && !templateModelName.equalsIgnoreCase(runtimeModelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前音色与官方 TTS 模型不兼容");
        }
        String effectiveModelName = runtimeContext.customModeActive() && StringUtils.hasText(templateModelName)
                ? templateModelName
                : runtimeModelName;
        if (!StringUtils.hasText(effectiveModelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写 TTS 模型，或在模板里配置推荐模型");
        }
        String fingerprint = buildConfigFingerprint(template, runtimeContext, effectiveModelName);
        long instanceOwnerId = instanceOwnerIdForRuntime(userId, runtimeContext);
        String lockKey = instanceOwnerId + ":" + blank(template.getTemplateCode()) + ":" + fingerprint;
        Object lock = TEMPLATE_PROVISION_LOCKS[Math.floorMod(lockKey.hashCode(), TEMPLATE_PROVISION_LOCKS.length)];
        synchronized (lock) {
            requireRemainingSeconds(deadlineNanos);
            return resolveOrProvisionVoice(
                    instanceOwnerId, template, runtimeContext, effectiveModelName, fingerprint, deadlineNanos);
        }
    }

    static long instanceOwnerIdForRuntime(long userId, TtsRuntimeContext runtimeContext) {
        return runtimeContext != null && runtimeContext.customModeActive()
                ? userId
                : OFFICIAL_TEMPLATE_OWNER_ID;
    }

    public boolean isTemplateCompatible(String templateCode, TtsRuntimeContext runtimeContext) {
        AppTtsVoiceTemplate template = templateService.findEnabledTemplate(templateCode);
        if (template == null || runtimeContext == null
                || !runtimeContext.providerMatches(blank(template.getProviderSource()))) {
            return false;
        }
        String configuredModel = blank(template.getTtsModelName());
        return runtimeContext.customModeActive()
                || !StringUtils.hasText(configuredModel)
                || configuredModel.equalsIgnoreCase(runtimeContext.effectiveModelName(""));
    }

    private ResolvedVoice resolveOrProvisionVoice(
            long instanceOwnerId,
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName,
            String fingerprint,
            long deadlineNanos
    ) {
        requireRemainingSeconds(deadlineNanos);
        AppUserTtsVoiceInstance instance = instanceMapper.findByUserIdAndTemplateCode(
                instanceOwnerId, blank(template.getTemplateCode()));
        if (instance != null
                && fingerprint.equals(blank(instance.getConfigFingerprint()))
                && "ready".equalsIgnoreCase(blank(instance.getStatus()))
                && StringUtils.hasText(instance.getVoiceUri())) {
            return new ResolvedVoice(
                    blank(instance.getVoiceUri()), effectiveModelName,
                    blank(template.getTemplateCode()), blank(template.getDisplayName()));
        }
        if (instance == null) {
            instance = new AppUserTtsVoiceInstance();
            instance.setUserId(instanceOwnerId);
            instance.setTemplateCode(blank(template.getTemplateCode()));
        }
        instance.setProviderSource(blank(runtimeContext.providerSource()));
        instance.setBaseUrl(blank(runtimeContext.baseUrl()));
        instance.setModelName(effectiveModelName);
        instance.setConfigFingerprint(fingerprint);
        instance.setStatus("pending");
        instance.setLastError("");
        try {
            ReferenceAudio referenceAudio = loadReferenceAudio(template, deadlineNanos);
            String voiceUri = uploadDynamicVoice(
                    template, runtimeContext, effectiveModelName, referenceAudio, deadlineNanos);
            requireRemainingSeconds(deadlineNanos);
            if (!StringUtils.hasText(voiceUri)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "平台没有返回可用的音色标识");
            }
            instance.setVoiceUri(voiceUri);
            instance.setStatus("ready");
            instance.setLastError("");
            persistInstance(instance);
            return new ResolvedVoice(
                    voiceUri, effectiveModelName,
                    blank(template.getTemplateCode()), blank(template.getDisplayName()));
        } catch (BusinessException ex) {
            instance.setVoiceUri("");
            instance.setStatus("failed");
            instance.setLastError(trim(ex.getMessage(), 255));
            persistInstance(instance);
            throw ex;
        }
    }

    public ProvisionedUserVoice provisionUserVoice(
            long userId,
            long voiceId,
            String sampleText,
            byte[] audioBytes,
            String mimeType,
            TtsRuntimeContext runtimeContext
    ) {
        requireSiliconFlowByok(runtimeContext);
        String modelName = runtimeContext.effectiveModelName("");
        if (!StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先在 AI 设置中配置硅基流动 TTS 模型");
        }
        validateReferenceAudioSize(audioBytes);
        String safeMimeType = MediaPayloadValidator.requireAudio(audioBytes, mimeType);
        String safeText = trim(sampleText, 255);
        if (!StringUtils.hasText(safeText)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请填写参考音频中准确的朗读文本");
        }
        AppTtsVoiceTemplate request = new AppTtsVoiceTemplate();
        request.setTemplateCode("user-" + userId + "-" + voiceId);
        request.setSampleScript(safeText);
        String voiceUri = uploadDynamicVoice(
                request,
                runtimeContext,
                modelName,
                new ReferenceAudio(audioBytes, safeMimeType)
        );
        if (!StringUtils.hasText(voiceUri)) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动没有返回可用的音色标识");
        }
        return new ProvisionedUserVoice(
                voiceUri,
                modelName,
                buildRuntimeFingerprint(runtimeContext, modelName)
        );
    }

    public ProviderAccount getProviderAccount(TtsRuntimeContext runtimeContext) {
        requireSiliconFlowByok(runtimeContext);
        JsonNode root = getProviderJson(runtimeContext, "/user/info");
        JsonNode data = root.path("data").isObject()
                ? root.path("data")
                : (root.path("result").isObject() ? root.path("result") : root);
        return new ProviderAccount(
                scalarText(data.path("balance")),
                scalarText(data.path("chargeBalance")),
                scalarText(data.path("totalBalance"))
        );
    }

    public List<ProviderVoice> listProviderVoices(TtsRuntimeContext runtimeContext) {
        requireSiliconFlowByok(runtimeContext);
        JsonNode root = getProviderJson(runtimeContext, "/audio/voice/list");
        JsonNode rows = findProviderVoiceArray(root);
        if (rows == null || !rows.isArray()) return List.of();
        List<ProviderVoice> result = new ArrayList<>();
        for (JsonNode item : rows) {
            if (result.size() >= 100) break;
            if (item == null || !item.isObject()) continue;
            String voiceUri = trim(firstText(item, "uri", "voiceUri", "voice_uri"), 255);
            if (!StringUtils.hasText(voiceUri)) continue;
            String displayName = trim(firstText(item, "customName", "name", "displayName"), 64);
            if (!StringUtils.hasText(displayName)) displayName = providerVoiceFallbackName(voiceUri);
            result.add(new ProviderVoice(
                    voiceUri,
                    displayName,
                    trim(firstText(item, "model", "modelName", "model_name"), 255),
                    trim(firstText(item, "text", "sampleText", "sample_text"), 255)
            ));
        }
        return List.copyOf(result);
    }

    public void deleteProviderVoice(TtsRuntimeContext runtimeContext, String voiceUri) {
        requireSiliconFlowByok(runtimeContext);
        String safeVoiceUri = trim(voiceUri, 255);
        if (!StringUtils.hasText(safeVoiceUri)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "音色标识无效");
        }
        RestClient client = buildRestClient(blank(runtimeContext.baseUrl()), blank(runtimeContext.apiKey()));
        try {
            client.post()
                    .uri("/audio/voice/deletions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("uri", safeVoiceUri))
                    .exchange((request, response) -> {
                        byte[] body = BoundedHttpBodyHandlers.readBytes(
                                response.getBody(), MAX_VOICE_API_RESPONSE_BYTES);
                        if (response.getStatusCode().value() == 404) return null;
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                                    providerErrorMessage(new String(body, StandardCharsets.UTF_8)));
                        }
                        requireProviderSuccess(new String(body, StandardCharsets.UTF_8));
                        return null;
                    });
        } catch (BusinessException ex) {
            throw ex;
        } catch (BoundedHttpBodyHandlers.BodyTooLargeException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动返回内容过大");
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动音色删除服务暂时不可用");
        }
    }

    public static String buildRuntimeFingerprint(TtsRuntimeContext runtimeContext, String modelName) {
        return sha256Hex(String.join("|",
                blank(runtimeContext == null ? null : runtimeContext.providerSource()),
                blank(runtimeContext == null ? null : runtimeContext.baseUrl()),
                blank(runtimeContext == null ? null : runtimeContext.apiKey()),
                blank(modelName)
        ));
    }

    private static void requireSiliconFlowByok(TtsRuntimeContext runtimeContext) {
        if (runtimeContext == null || !runtimeContext.customModeActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "自建音色必须使用你自己的硅基流动 API Key");
        }
        if (!"siliconflow".equalsIgnoreCase(blank(runtimeContext.providerSource()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "第一期自建音色仅支持硅基流动");
        }
        if (!runtimeContext.hasApiKey()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写硅基流动 TTS API Key");
        }
    }

    public static String buildConfigFingerprint(
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName
    ) {
        String joined = String.join("|",
                blank(runtimeContext == null ? null : runtimeContext.providerSource()),
                blank(runtimeContext == null ? null : runtimeContext.baseUrl()),
                blank(runtimeContext == null ? null : runtimeContext.apiKey()),
                blank(effectiveModelName),
                blank(template == null ? null : template.getTemplateCode()),
                blank(template == null ? null : template.getReferenceAudioUrl()),
                blank(template == null ? null : template.getSampleScript()),
                String.valueOf(template != null && template.getUpdatedAt() != null ? template.getUpdatedAt() : "")
        );
        return sha256Hex(joined);
    }

    private void persistInstance(AppUserTtsVoiceInstance instance) {
        if (instance.getId() == null) {
            instanceMapper.insert(instance);
            return;
        }
        instanceMapper.updateById(instance);
    }

    private String uploadDynamicVoice(
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName,
            ReferenceAudio referenceAudio
    ) {
        long deadlineNanos = System.nanoTime() + DEFAULT_PROVISION_TIMEOUT_SECONDS * NANOS_PER_SECOND;
        return uploadDynamicVoice(template, runtimeContext, effectiveModelName, referenceAudio, deadlineNanos);
    }

    private String uploadDynamicVoice(
            AppTtsVoiceTemplate template,
            TtsRuntimeContext runtimeContext,
            String effectiveModelName,
            ReferenceAudio referenceAudio,
            long deadlineNanos
    ) {
        RestClient client = buildRestClient(
                blank(runtimeContext.baseUrl()), blank(runtimeContext.apiKey()),
                requireRemainingSeconds(deadlineNanos));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", effectiveModelName);
        payload.put("customName", buildCustomVoiceName(template));
        payload.put("text", blank(template.getSampleScript()));
        payload.put("audio", "data:" + referenceAudio.mimeType() + ";base64," + Base64.getEncoder().encodeToString(referenceAudio.bytes()));
        try {
            String raw = client.post()
                    .uri("/uploads/audio/voice")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        byte[] body = BoundedHttpBodyHandlers.readBytes(
                                response.getBody(), MAX_VOICE_API_RESPONSE_BYTES);
                        String responseText = new String(body, StandardCharsets.UTF_8);
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(
                                    ErrorCode.UPSTREAM_ERROR, providerErrorMessage(responseText));
                        }
                        return responseText;
                    });
            requireRemainingSeconds(deadlineNanos);
            return extractVoiceUri(raw);
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, providerErrorMessage(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模板音色生成服务暂时不可用");
        }
    }

    private RestClient buildRestClient(String baseUrl, String apiKey) {
        return buildRestClient(baseUrl, apiKey, DEFAULT_PROVISION_TIMEOUT_SECONDS);
    }

    private RestClient buildRestClient(String baseUrl, String apiKey, int timeoutSeconds) {
        String safeBaseUrl = OutboundUrlGuard.requirePublicHttpUrl(
                baseUrl, "音色服务地址不安全，请使用可公开访问的 HTTP(S) 地址").toString();
        int safeTimeoutSeconds = Math.max(1, Math.min(DEFAULT_PROVISION_TIMEOUT_SECONDS, timeoutSeconds));
        HttpClient requestHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(CONNECT_TIMEOUT.toSeconds(), safeTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(requestHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(safeTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(safeBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private JsonNode getProviderJson(TtsRuntimeContext runtimeContext, String path) {
        RestClient client = buildRestClient(blank(runtimeContext.baseUrl()), blank(runtimeContext.apiKey()));
        try {
            String raw = client.get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        byte[] body = BoundedHttpBodyHandlers.readBytes(
                                response.getBody(), MAX_VOICE_API_RESPONSE_BYTES);
                        String responseText = new String(body, StandardCharsets.UTF_8);
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, providerErrorMessage(responseText));
                        }
                        return responseText;
                    });
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            if (root == null) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动返回内容为空");
            }
            requireProviderSuccess(root, raw);
            return root;
        } catch (BusinessException ex) {
            throw ex;
        } catch (BoundedHttpBodyHandlers.BodyTooLargeException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动返回内容过大");
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动账号服务暂时不可用");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动返回了无法识别的数据");
        }
    }

    private static JsonNode findProviderVoiceArray(JsonNode root) {
        if (root == null) return null;
        if (root.isArray()) return root;
        for (String key : List.of("result", "data", "voices", "items")) {
            JsonNode child = root.path(key);
            if (child.isArray()) return child;
            if (child.isObject()) {
                for (String nestedKey : List.of("result", "data", "voices", "items")) {
                    JsonNode nested = child.path(nestedKey);
                    if (nested.isArray()) return nested;
                }
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null) return "";
        for (String key : keys) {
            String value = scalarText(node.path(key));
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private static String scalarText(JsonNode node) {
        return node != null && node.isValueNode() ? blank(node.asText("")) : "";
    }

    private static String providerVoiceFallbackName(String voiceUri) {
        String value = blank(voiceUri);
        int index = Math.max(value.lastIndexOf(':'), value.lastIndexOf('/'));
        String suffix = index >= 0 && index + 1 < value.length() ? value.substring(index + 1) : value;
        suffix = trim(suffix, 48);
        return StringUtils.hasText(suffix) ? suffix : "硅基流动音色";
    }

    private void requireProviderSuccess(String raw) {
        if (!StringUtils.hasText(raw)) return;
        try {
            requireProviderSuccess(objectMapper.readTree(raw), raw);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "硅基流动返回了无法识别的数据");
        }
    }

    private void requireProviderSuccess(JsonNode root, String raw) {
        if (root == null || root.isArray()) return;
        JsonNode status = root.path("status");
        if (status.isBoolean() && !status.asBoolean()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, providerErrorMessage(raw));
        }
        JsonNode code = root.path("code");
        if (!code.isValueNode()) return;
        String value = code.asText("").trim();
        if (!value.isBlank() && !"0".equals(value) && !"200".equals(value) && !"20000".equals(value)) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, providerErrorMessage(raw));
        }
    }

    private String extractVoiceUri(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            String uri = blank(root.path("uri").asText(""));
            if (StringUtils.hasText(uri)) {
                return uri;
            }
            uri = blank(root.path("data").path("uri").asText(""));
            if (StringUtils.hasText(uri)) {
                return uri;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String providerErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody == null ? "" : responseBody);
            String message = blank(root.path("error").path("message").asText(""));
            if (StringUtils.hasText(message)) {
                return normalizeProviderMessage(message);
            }
            message = blank(root.path("message").asText(""));
            if (StringUtils.hasText(message)) {
                return normalizeProviderMessage(message);
            }
        } catch (Exception ignored) {
        }
        return "模板音色生成失败，请检查模型和参考音频";
    }

    private String normalizeProviderMessage(String rawMessage) {
        String message = blank(rawMessage);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("voice cloning") || lower.contains("voice") && lower.contains("unsupported")) {
            return "当前 TTS 模型不支持动态音色，请换成硅基流动支持音色克隆的模型";
        }
        if (lower.contains("audio")) {
            return "参考音频不符合要求，请换成 5 到 20 秒、清晰干净的人声音频";
        }
        if (lower.contains("api key") || lower.contains("unauthorized") || lower.contains("authentication")) {
            return "当前 TTS 的 API Key 不可用，请先检查硅基流动配置";
        }
        return message;
    }

    private String buildCustomVoiceName(AppTtsVoiceTemplate template) {
        String base = blank(template.getTemplateCode()).replaceAll("[^a-zA-Z0-9_-]+", "-");
        if (!StringUtils.hasText(base)) {
            base = "voice-template";
        }
        return trim("jg-" + base, 64);
    }

    private ReferenceAudio loadReferenceAudio(AppTtsVoiceTemplate template, long deadlineNanos) {
        requireRemainingSeconds(deadlineNanos);
        String url = blank(template.getReferenceAudioUrl());
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模板还没有配置参考音频");
        }
        if (url.startsWith("/uploads/h5/")) {
            byte[] bytes = uploadService.readUploadedFileBytes(url);
            requireRemainingSeconds(deadlineNanos);
            validateReferenceAudioSize(bytes);
            String mimeType = MediaPayloadValidator.requireAudio(
                    bytes, uploadService.detectUploadedFileContentType(url));
            return new ReferenceAudio(bytes, mimeType);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频地址不可用");
        }
        try {
            URI safeUri = OutboundUrlGuard.requirePublicHttpUrl(url, "参考音频地址不安全");
            int timeoutSeconds = Math.min(
                    REFERENCE_AUDIO_TIMEOUT_SECONDS, requireRemainingSeconds(deadlineNanos));
            HttpRequest request = HttpRequest.newBuilder(safeUri)
                    .GET()
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request, BoundedHttpBodyHandlers.ofByteArray(MAX_REFERENCE_AUDIO_BYTES));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考音频下载失败");
            }
            byte[] bytes = response.body() == null ? new byte[0] : response.body();
            requireRemainingSeconds(deadlineNanos);
            validateReferenceAudioSize(bytes);
            String contentType = MediaPayloadValidator.requireAudio(
                    bytes, response.headers().firstValue("Content-Type").orElse(""));
            return new ReferenceAudio(bytes, contentType);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考音频下载失败");
        }
    }

    static int remainingSeconds(long deadlineNanos, long nowNanos) {
        long remainingNanos = deadlineNanos - nowNanos;
        if (remainingNanos <= 0) return 0;
        long roundedUp = (remainingNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
        return (int) Math.max(1, Math.min(DEFAULT_PROVISION_TIMEOUT_SECONDS, roundedUp));
    }

    private static int requireRemainingSeconds(long deadlineNanos) {
        int remainingSeconds = remainingSeconds(deadlineNanos, System.nanoTime());
        if (remainingSeconds <= 0) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音合成等待超时，请重试");
        }
        return remainingSeconds;
    }

    private void validateReferenceAudioSize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频不能为空");
        }
        if (bytes.length > MAX_REFERENCE_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频太大，建议控制在 8MB 以内");
        }
    }

    private record ReferenceAudio(byte[] bytes, String mimeType) {
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(blank(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                sb.append(Character.forDigit((item >> 4) & 0xF, 16));
                sb.append(Character.forDigit(item & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Object[] createProvisionLocks(int count) {
        Object[] locks = new Object[Math.max(1, count)];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private static String trim(String value, int maxLength) {
        String text = blank(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
