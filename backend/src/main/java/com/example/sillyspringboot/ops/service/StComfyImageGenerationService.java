package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StComfyImageGenerationService implements ImageGenerationEngine {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%[a-zA-Z0-9_]+%");

    private final AppImageGenerationSettingsService settingsService;
    private final H5EntitlementService entitlementService;
    private final ImageGenerationConcurrencyGate concurrencyGate;
    private final AppCharacterMapper characterMapper;
    private final H5StAssetUrls stAssetUrls;
    private final StClient stClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public StComfyImageGenerationService(
            AppImageGenerationSettingsService settingsService,
            H5EntitlementService entitlementService,
            ImageGenerationConcurrencyGate concurrencyGate,
            AppCharacterMapper characterMapper,
            H5StAssetUrls stAssetUrls,
            StClient stClient,
            ObjectMapper objectMapper
    ) {
        this.settingsService = settingsService;
        this.entitlementService = entitlementService;
        this.concurrencyGate = concurrencyGate;
        this.characterMapper = characterMapper;
        this.stAssetUrls = stAssetUrls;
        this.stClient = stClient;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public String engineName() {
        return "st_comfy";
    }

    @Override
    @Transactional
    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        String safeClientUid = safe(clientUid);
        String prompt = trim(payload == null ? null : payload.get("prompt"), 4000);
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写生图提示词");
        }
        int count = clampInt(payload == null ? null : payload.get("count"), 1, 1, 1);
        long characterId = clampLong(payload == null ? null : payload.get("characterId"), 0L, 0L, Long.MAX_VALUE);
        String referenceImageUrl = safe(payload == null ? null : payload.get("referenceImageUrl"));
        String referencePolicy = normalizeReferencePolicy(payload == null ? null : payload.get("referencePolicy"));
        String requestOrigin = safe(payload == null ? null : payload.get("_requestOrigin"));
        int[] dimensions = resolveDimensions(payload);

        entitlementService.guardImageCharacterAccess(safeClientUid, characterId);
        H5EntitlementService.AccessTicket accessTicket = entitlementService.guardImage(safeClientUid, count, characterId);
        AppUser user = entitlementService.resolveUser(safeClientUid);

        try (ImageGenerationConcurrencyGate.Lease ignored = concurrencyGate.acquire(user.getId())) {
            WorkflowBuild build = buildWorkflow(
                    safeClientUid,
                    prompt,
                    dimensions[0],
                    dimensions[1],
                    characterId,
                    referenceImageUrl,
                    referencePolicy,
                    requestOrigin
            );
            StComfyResult result = requestComfy(build.workflowPrompt());
            String dataUrl = "data:image/" + result.format() + ";base64," + result.base64Data();
            entitlementService.recordSuccessfulImage(accessTicket, count);

            Map<String, Object> image = new LinkedHashMap<>();
            image.put("url", dataUrl);
            image.put("prompt", prompt);
            image.put("rawPrompt", prompt);
            image.put("width", dimensions[0]);
            image.put("height", dimensions[1]);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mode", "provider");
            data.put("usedCount", 0);
            data.put("remainingCount", entitlementService.currentRemainingImageQuota(user.getId()));
            data.put("providerSource", "managed");
            data.put("modelName", "managed-image-engine");
            data.put("promptEnhanced", false);
            data.put("referenceApplied", build.referenceApplied());
            data.put("referencePolicy", referencePolicy);
            data.put("images", List.of(image));
            if (StringUtils.hasText(build.warning())) {
                data.put("warning", build.warning());
            }
            data.put("message", "ok");
            return data;
        }
    }

    private WorkflowBuild buildWorkflow(
            String clientUid,
            String prompt,
            int width,
            int height,
            long characterId,
            String referenceImageUrl,
            String referencePolicy,
            String requestOrigin
    ) {
        AppImageGenerationSettings cfg = settingsService.getSettings();
        String workflowName = selectWorkflowName(referencePolicy);
        String template = loadWorkflowTemplate(workflowName);
        boolean workflowNeedsCharacterReference = containsPlaceholder(template, "char_avatar");
        boolean workflowNeedsUserReference = containsPlaceholder(template, "user_avatar");
        boolean workflowSupportsReference = workflowNeedsCharacterReference || workflowNeedsUserReference;

        if ("reference_only".equals(referencePolicy) && !workflowSupportsReference) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "当前生图模式不支持参考图强一致，请联系管理员调整生图配置"
            );
        }

        ReferenceImage referenceImage = null;
        String warning = "";
        if (workflowSupportsReference || shouldAttemptReferenceImage(referencePolicy)) {
            referenceImage = resolveReferenceImage(clientUid, characterId, referenceImageUrl, requestOrigin);
            if (referenceImage == null) {
                if ("reference_only".equals(referencePolicy) || workflowSupportsReference) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前角色没有可用参考图，请先确认角色头像可访问");
                }
                warning = weakConsistencyWarning("当前角色没有可用参考图");
            }
        }

        String modelName = safe(cfg.getModel());
        if (containsPlaceholder(template, "model") && !StringUtils.hasText(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置不完整，请联系管理员检查模型配置");
        }

        String workflow = template;
        workflow = replaceJson(workflow, "prompt", prompt);
        workflow = replaceJson(workflow, "negative_prompt", cfg.getNegativePrompt());
        workflow = replaceJson(workflow, "width", width);
        workflow = replaceJson(workflow, "height", height);
        workflow = replaceJson(workflow, "seed", resolveSeed(cfg.getSeed()));
        workflow = replaceJson(workflow, "steps", clampInt(cfg.getSteps(), 28, 1, 150));
        workflow = replaceJson(workflow, "scale", cfg.getScale());
        workflow = replaceJson(workflow, "sampler", firstNonBlank(cfg.getSampler(), "euler"));
        workflow = replaceJson(workflow, "scheduler", firstNonBlank(cfg.getScheduler(), "normal"));
        workflow = replaceJson(workflow, "model", modelName);
        workflow = replaceJson(workflow, "denoise", cfg.getDenoise());
        workflow = replaceJson(workflow, "clip_skip", -1);
        if (referenceImage != null) {
            String base64 = Base64.getEncoder().encodeToString(referenceImage.bytes());
            workflow = replaceJson(workflow, "char_avatar", base64);
            workflow = replaceJson(workflow, "user_avatar", base64);
        }
        rejectUnresolvedPlaceholders(workflow);
        validateWorkflowJson(workflow);
        return new WorkflowBuild(
                workflowName,
                modelName,
                "{\n  \"prompt\": " + workflow + "\n}",
                referenceImage != null && workflowSupportsReference,
                warning
        );
    }

    private StComfyResult requestComfy(String workflowPrompt) {
        AppImageGenerationSettings cfg = settingsService.getSettings();
        String comfyUrl = safe(cfg.getComfyUrl());
        if (!StringUtils.hasText(comfyUrl)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置不完整，请联系管理员检查配置");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", comfyUrl);
            body.put("prompt", workflowPrompt);
            String raw = stClient.generateComfyImage(body);
            JsonNode root = objectMapper.readTree(raw == null ? "" : raw);
            String format = firstNonBlank(text(root.path("format")), "png").toLowerCase();
            String data = text(root.path("data"));
            if (!StringUtils.hasText(data)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图服务没有返回图片，请稍后重试");
            }
            return new StComfyResult(format, data);
        } catch (StUnavailableException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图服务暂不可用，请稍后重试或联系管理员");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, readableEngineError(ex.getResponseBodyAsString()));
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图服务返回异常，请稍后重试");
        }
    }

    private String loadWorkflowTemplate(String workflowName) {
        try {
            String workflow = stClient.loadComfyWorkflow(workflowName);
            if (!StringUtils.hasText(workflow)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图服务配置不完整，请联系管理员检查配置");
            }
            return workflow;
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, readableEngineError(ex.getResponseBodyAsString()));
        } catch (StUnavailableException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图服务暂不可用，请稍后重试或联系管理员");
        }
    }

    private String selectWorkflowName(String referencePolicy) {
        AppImageGenerationSettings cfg = settingsService.getSettings();
        if ("reference_only".equals(referencePolicy) && StringUtils.hasText(cfg.getReferenceWorkflow())) {
            return cfg.getReferenceWorkflow();
        }
        return firstNonBlank(cfg.getWorkflow(), "Default_Comfy_Workflow.json");
    }

    private ReferenceImage resolveReferenceImage(String clientUid, long characterId, String referenceImageUrl, String requestOrigin) {
        String safeReference = safe(referenceImageUrl);
        if (safeReference.startsWith("data:")) {
            return fetchReferenceImage(safeReference, requestOrigin);
        }
        BusinessException referenceException = null;
        if (StringUtils.hasText(safeReference)) {
            try {
                return fetchReferenceImage(safeReference, requestOrigin);
            } catch (BusinessException ex) {
                referenceException = ex;
            }
        }
        if (characterId > 0) {
            ReferenceImage fromCharacter = fetchCharacterReferenceImage(clientUid, characterId, requestOrigin);
            if (fromCharacter != null) {
                return fromCharacter;
            }
            if (referenceException != null) {
                throw referenceException;
            }
            return null;
        }
        if (referenceException != null) {
            throw referenceException;
        }
        return null;
    }

    private ReferenceImage fetchCharacterReferenceImage(String clientUid, long characterId, String requestOrigin) {
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            return null;
        }
        entitlementService.guardImageCharacterAccess(clientUid, characterId);
        String[] candidates = new String[] {
                absolutizeTrustedReferenceUrl(stAssetUrls.resolveWithPreset(character.getStAvatarUrl(), "detail"), requestOrigin),
                absolutizeTrustedReferenceUrl(stAssetUrls.resolve(character.getStAvatarUrl()), requestOrigin),
                absolutizeTrustedReferenceUrl(character.getAvatarUrl(), requestOrigin),
                absolutizeTrustedReferenceUrl(character.getCoverUrl(), requestOrigin),
                absolutizeTrustedReferenceUrl(
                        stAssetUrls.portraitForCharacterThumb(
                                character.getAvatarUrl(),
                                character.getCoverUrl(),
                                character.getStAvatarUrl(),
                                "detail"
                        ),
                        requestOrigin
                ),
                absolutizeTrustedReferenceUrl(
                        stAssetUrls.portraitForCharacter(
                                character.getAvatarUrl(),
                                character.getCoverUrl(),
                                character.getStAvatarUrl()
                        ),
                        requestOrigin
                )
        };
        BusinessException lastException = null;
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            try {
                return fetchReferenceImage(candidate, requestOrigin);
            } catch (BusinessException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return null;
    }

    private ReferenceImage fetchReferenceImage(String referenceImageUrl, String requestOrigin) {
        String safeUrl = safe(referenceImageUrl);
        if (!StringUtils.hasText(safeUrl)) {
            return null;
        }
        if (safeUrl.startsWith("/")) {
            safeUrl = absolutizeRelativeUrl(safeUrl, requestOrigin);
        }
        if (safeUrl.startsWith("data:")) {
            return decodeDataUrlReferenceImage(safeUrl);
        }
        if (!isAllowedReferenceImageUrl(safeUrl, requestOrigin)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图地址无效，请使用本站角色卡图片");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(safeUrl))
                    .timeout(settingsService.getSettings().getRequestTimeout())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().length == 0) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "角色参考图获取失败");
            }
            String contentType = firstNonBlank(response.headers().firstValue("Content-Type").orElse(""), "image/png");
            return new ReferenceImage(response.body(), contentType);
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "角色参考图获取失败");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "角色参考图获取失败");
        }
    }

    private ReferenceImage decodeDataUrlReferenceImage(String dataUrl) {
        String safeDataUrl = safe(dataUrl);
        int commaIndex = safeDataUrl.indexOf(',');
        if (commaIndex <= 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图数据格式不正确");
        }
        String meta = safeDataUrl.substring(5, commaIndex);
        String body = safeDataUrl.substring(commaIndex + 1);
        if (!meta.toLowerCase().contains(";base64")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图必须是 base64 图片数据");
        }
        String contentType = meta.split(";", 2)[0].trim();
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/png";
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(body);
            if (bytes.length == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图数据不能为空");
            }
            return new ReferenceImage(bytes, contentType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图数据解码失败");
        }
    }

    private String absolutizeTrustedReferenceUrl(String rawUrl, String requestOrigin) {
        String safeUrl = safe(rawUrl);
        if (!StringUtils.hasText(safeUrl)) {
            return "";
        }
        if (safeUrl.startsWith("data:")) {
            return safeUrl;
        }
        if (safeUrl.startsWith("/")) {
            return absolutizeRelativeUrl(safeUrl, requestOrigin);
        }
        if (safeUrl.startsWith("http://") || safeUrl.startsWith("https://")) {
            return isAllowedReferenceImageUrl(safeUrl, requestOrigin) ? safeUrl : "";
        }
        return "";
    }

    private String absolutizeRelativeUrl(String path, String requestOrigin) {
        String safePath = safe(path);
        if (!StringUtils.hasText(safePath) || !safePath.startsWith("/")) {
            return "";
        }
        try {
            URI origin = URI.create(safe(requestOrigin));
            if (!origin.isAbsolute()) {
                return "";
            }
            return origin.resolve(safePath).toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isAllowedReferenceImageUrl(String referenceImageUrl, String requestOrigin) {
        try {
            URI target = URI.create(referenceImageUrl);
            if (!target.isAbsolute()) {
                return false;
            }
            String scheme = safe(target.getScheme()).toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return false;
            }
            URI origin = URI.create(safe(requestOrigin));
            if (!origin.isAbsolute()) {
                return false;
            }
            return safe(origin.getScheme()).equalsIgnoreCase(safe(target.getScheme()))
                    && safe(origin.getHost()).equalsIgnoreCase(safe(target.getHost()))
                    && normalizePort(origin) == normalizePort(target);
        } catch (Exception ex) {
            return false;
        }
    }

    private static String replaceJson(String workflow, String placeholder, Object value) {
        String jsonValue;
        try {
            jsonValue = new ObjectMapper().writeValueAsString(value);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生图请求组装失败");
        }
        return workflow.replace("\"%" + placeholder + "%\"", jsonValue);
    }

    private void validateWorkflowJson(String workflow) {
        try {
            objectMapper.readTree(workflow);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置错误，请联系管理员检查配置");
        }
    }

    private static void rejectUnresolvedPlaceholders(String workflow) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(workflow);
        if (matcher.find()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置不完整，请联系管理员检查配置");
        }
    }

    private static boolean containsPlaceholder(String workflow, String name) {
        return workflow != null && workflow.contains("%" + name + "%");
    }

    private static String readableEngineError(String raw) {
        String text = safe(raw);
        if (!StringUtils.hasText(text)) {
            return "生图服务处理失败，请稍后重试";
        }
        String lower = text.toLowerCase();
        if (lower.contains("comfy")
                || lower.contains("sillytavern")
                || lower.contains("workflow")
                || lower.contains("checkpoint")
                || lower.contains("prompt_id")
                || lower.contains("node")) {
            return "生图服务处理失败，请稍后重试或联系管理员";
        }
        if (text.length() > 120) {
            text = text.substring(0, 120);
        }
        return text;
    }

    private static int[] resolveDimensions(Map<String, Object> payload) {
        String aspectRatio = normalizeAspectRatio(payload == null ? null : payload.get("aspectRatio"));
        int[] defaults = aspectRatioToDimensions(aspectRatio);
        int width = clampInt(payload == null ? null : payload.get("width"), defaults[0], 256, 2048);
        int height = clampInt(payload == null ? null : payload.get("height"), defaults[1], 256, 2048);
        return new int[] {width, height};
    }

    private static int[] aspectRatioToDimensions(String aspectRatio) {
        return switch (safe(aspectRatio)) {
            case "square" -> new int[] {1024, 1024};
            case "landscape", "wide" -> new int[] {1536, 1024};
            default -> new int[] {1024, 1536};
        };
    }

    private static String normalizeAspectRatio(Object value) {
        String safe = safe(value);
        if ("square".equals(safe) || "landscape".equals(safe) || "wide".equals(safe)) {
            return safe;
        }
        return "portrait";
    }

    private static String normalizeReferencePolicy(Object rawPolicy) {
        String text = safe(rawPolicy).toLowerCase();
        if ("prompt_first".equals(text) || "prompt-priority".equals(text) || "prompt_priority".equals(text) || "free".equals(text)) {
            return "prompt_first";
        }
        if ("balanced".equals(text) || "reference_soft".equals(text) || "reference-soft".equals(text) || "identity_balance".equals(text)) {
            return "balanced";
        }
        if ("reference_only".equals(text) || "reference_required".equals(text) || "reference_first".equals(text)) {
            return "reference_only";
        }
        return "auto";
    }

    private static boolean shouldAttemptReferenceImage(String referencePolicy) {
        String policy = safe(referencePolicy).toLowerCase();
        return "reference_only".equals(policy) || "balanced".equals(policy) || "auto".equals(policy);
    }

    private static long resolveSeed(long configuredSeed) {
        if (configuredSeed >= 0L) {
            return configuredSeed;
        }
        return ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    private static String weakConsistencyWarning(String reason) {
        String prefix = StringUtils.hasText(reason) ? reason + "，" : "";
        return prefix + "当前生图模式未使用参考图，可能不完全像同一个角色";
    }

    private static int normalizePort(URI uri) {
        if (uri == null) {
            return -1;
        }
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static int clampInt(Object value, int fallback, int min, int max) {
        int raw;
        if (value instanceof Number number) {
            raw = number.intValue();
        } else {
            try {
                raw = Integer.parseInt(String.valueOf(value).trim());
            } catch (Exception ex) {
                raw = fallback;
            }
        }
        return Math.max(min, Math.min(max, raw));
    }

    private static long clampLong(Object value, long fallback, long min, long max) {
        long raw;
        if (value instanceof Number number) {
            raw = number.longValue();
        } else {
            try {
                raw = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ex) {
                raw = fallback;
            }
        }
        return Math.max(min, Math.min(max, raw));
    }

    private static String trim(Object value, int maxLength) {
        String text = String.valueOf(value == null ? "" : value).replace("\r", " ").trim();
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record WorkflowBuild(
            String workflowName,
            String modelName,
            String workflowPrompt,
            boolean referenceApplied,
            String warning
    ) {}

    private record StComfyResult(String format, String base64Data) {}

    private record ReferenceImage(byte[] bytes, String contentType) {}
}
