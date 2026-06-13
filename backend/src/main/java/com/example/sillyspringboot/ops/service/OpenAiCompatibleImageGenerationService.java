package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
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

@Service
public class OpenAiCompatibleImageGenerationService implements ImageGenerationEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleImageGenerationService.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration PROMPT_ENHANCE_TIMEOUT = Duration.ofSeconds(18);

    private final H5EntitlementService entitlementService;
    private final H5UserAiProviderService userAiProviderService;
    private final AppCharacterMapper characterMapper;
    private final H5StAssetUrls stAssetUrls;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleImageGenerationService(
            H5EntitlementService entitlementService,
            H5UserAiProviderService userAiProviderService,
            AppCharacterMapper characterMapper,
            H5StAssetUrls stAssetUrls,
            ObjectMapper objectMapper
    ) {
        this.entitlementService = entitlementService;
        this.userAiProviderService = userAiProviderService;
        this.characterMapper = characterMapper;
        this.stAssetUrls = stAssetUrls;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public String engineName() {
        return "openai_compatible";
    }

    @Transactional
    @Override
    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        String safeClientUid = safe(clientUid);
        String prompt = trim(payload == null ? null : payload.get("prompt"), 4000);
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写生图提示词");
        }
        int count = clampInt(payload == null ? null : payload.get("count"), 1, 1, 1);
        long characterId = clampLong(payload == null ? null : payload.get("characterId"), 0L, 0L, Long.MAX_VALUE);
        String aspectRatio = normalizeAspectRatio(payload == null ? null : payload.get("aspectRatio"));
        String referenceImageUrl = safe(payload == null ? null : payload.get("referenceImageUrl"));
        String referencePolicy = normalizeReferencePolicy(payload == null ? null : payload.get("referencePolicy"));
        String requestOrigin = safe(payload == null ? null : payload.get("_requestOrigin"));

        entitlementService.guardImageCharacterAccess(safeClientUid, characterId);
        H5EntitlementService.AccessTicket accessTicket = entitlementService.guardImage(safeClientUid, count, characterId);
        AppUser user = entitlementService.resolveUser(safeClientUid);
        UserModelOverride override = userAiProviderService.resolveActiveOverrideForUser(user.getId());
        if (override == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先在 AI 设置中配置生图平台、生图模型和 API Key");
        }

        String providerSource = safe(override.imageProviderSourceOrFallback());
        String modelName = safe(override.imageModelOrFallback());
        String apiKey = safe(override.imageApiKeyOrFallback());
        String customUrl = safe(override.imageCustomUrlOrFallback());
        if (!StringUtils.hasText(providerSource) || !StringUtils.hasText(modelName) || !StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图配置不完整，请先在 AI 设置中补全平台、模型和 API Key");
        }
        if (!looksLikeImageGenerationModel(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前模型看起来不是生图模型，请切换为文生图或参考图编辑模型");
        }

        String baseUrl = normalizeImageBaseUrl(providerSource, userAiProviderService.resolveProviderBaseUrlForSource(providerSource, customUrl));
        PromptEnhancementResult promptEnhancement = tryEnhancePromptWithUserTextModel(
                override,
                prompt,
                modelName,
                providerSource,
                referencePolicy
        );
        String effectivePrompt = promptEnhancement.prompt();
        ReferenceImage referenceImage = null;
        String referenceWarning = "";
        if (shouldAttemptReferenceImage(referencePolicy)) {
            try {
                referenceImage = resolveReferenceImage(safeClientUid, characterId, referenceImageUrl, requestOrigin);
                if (referenceImage == null) {
                    if ("reference_only".equals(referencePolicy)) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前角色没有可用参考图，请先确认角色头像可访问，或切换到平衡/自由生图模式");
                    }
                    referenceWarning = weakConsistencyWarning("当前角色没有可用参考图");
                }
            } catch (BusinessException ex) {
                if ("reference_only".equals(referencePolicy)) {
                    throw ex;
                }
                referenceWarning = weakConsistencyWarning("参考图暂不可用");
            }
        }
        ProviderImageResult imageResult = requestImagePromptAware(
                providerSource,
                baseUrl,
                apiKey,
                modelName,
                effectivePrompt,
                aspectRatio,
                count,
                referenceImage,
                referencePolicy,
                referenceWarning
        );
        entitlementService.recordSuccessfulImage(accessTicket, count);

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", imageResult.dataUrl());
        image.put("prompt", effectivePrompt);
        image.put("rawPrompt", prompt);
        image.put("width", imageResult.width());
        image.put("height", imageResult.height());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "provider");
        data.put("usedCount", count);
        data.put("remainingCount", entitlementService.currentRemainingImageQuota(user.getId()));
        data.put("providerSource", providerSource);
        data.put("modelName", modelName);
        data.put("promptEnhanced", promptEnhancement.enhanced());
        data.put("referenceApplied", imageResult.referenceApplied());
        data.put("referencePolicy", referencePolicy);
        data.put("images", List.of(image));
        String finalWarning = mergeWarnings(promptEnhancement.warning(), imageResult.warning());
        if (StringUtils.hasText(finalWarning)) {
            data.put("warning", finalWarning);
        }
        data.put("message", "ok");
        return data;
    }

    @Transactional
    public Map<String, Object> generateManaged(
            String clientUid,
            Map<String, Object> payload,
            String managedProviderSource,
            String managedImageModelName,
            String managedApiKey,
            String managedCustomUrl
    ) {
        String safeClientUid = safe(clientUid);
        String prompt = trim(payload == null ? null : payload.get("prompt"), 4000);
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写生图提示词");
        }
        int count = clampInt(payload == null ? null : payload.get("count"), 1, 1, 1);
        long characterId = clampLong(payload == null ? null : payload.get("characterId"), 0L, 0L, Long.MAX_VALUE);
        String aspectRatio = normalizeAspectRatio(payload == null ? null : payload.get("aspectRatio"));
        String referenceImageUrl = safe(payload == null ? null : payload.get("referenceImageUrl"));
        String referencePolicy = normalizeReferencePolicy(payload == null ? null : payload.get("referencePolicy"));
        String requestOrigin = safe(payload == null ? null : payload.get("_requestOrigin"));

        entitlementService.guardImageCharacterAccess(safeClientUid, characterId);
        H5EntitlementService.AccessTicket accessTicket = entitlementService.guardImage(safeClientUid, count, characterId);
        AppUser user = entitlementService.resolveUser(safeClientUid);

        String providerSource = safe(managedProviderSource);
        String modelName = safe(managedImageModelName);
        String apiKey = safe(managedApiKey);
        String customUrl = safe(managedCustomUrl);
        if (!StringUtils.hasText(providerSource) || !StringUtils.hasText(modelName) || !StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置不完整，请联系管理员检查平台、模型和 API Key");
        }
        if (!looksLikeImageGenerationModel(modelName)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务模型配置不正确，请联系管理员检查生图模型");
        }

        String baseUrl;
        try {
            baseUrl = normalizeImageBaseUrl(providerSource, userAiProviderService.resolveProviderBaseUrlForSource(providerSource, customUrl));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图服务配置不完整，请联系管理员检查接口地址");
        }

        ReferenceImage referenceImage = null;
        String referenceWarning = "";
        if (shouldAttemptReferenceImage(referencePolicy)) {
            try {
                referenceImage = resolveReferenceImage(safeClientUid, characterId, referenceImageUrl, requestOrigin);
                if (referenceImage == null) {
                    if ("reference_only".equals(referencePolicy)) {
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前角色没有可用参考图，请先确认角色头像可访问，或切换到自由生图模式");
                    }
                    referenceWarning = weakConsistencyWarning("当前角色没有可用参考图");
                }
            } catch (BusinessException ex) {
                if ("reference_only".equals(referencePolicy)) {
                    throw ex;
                }
                referenceWarning = weakConsistencyWarning("参考图暂不可用");
            }
        }

        ProviderImageResult imageResult;
        try {
            imageResult = requestImagePromptAware(
                    providerSource,
                    baseUrl,
                    apiKey,
                    modelName,
                    prompt,
                    aspectRatio,
                    count,
                    referenceImage,
                    referencePolicy,
                    referenceWarning
            );
        } catch (BusinessException ex) {
            throw new BusinessException(ex.getErrorCode(), managedProviderErrorMessage(ex.getMessage()), ex);
        }
        entitlementService.recordSuccessfulImage(accessTicket, count);

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", imageResult.dataUrl());
        image.put("prompt", prompt);
        image.put("rawPrompt", prompt);
        image.put("width", imageResult.width());
        image.put("height", imageResult.height());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "provider");
        data.put("usedCount", count);
        data.put("remainingCount", entitlementService.currentRemainingImageQuota(user.getId()));
        data.put("providerSource", "managed");
        data.put("modelName", "managed-image-engine");
        data.put("promptEnhanced", false);
        data.put("referenceApplied", imageResult.referenceApplied());
        data.put("referencePolicy", referencePolicy);
        data.put("images", List.of(image));
        if (StringUtils.hasText(imageResult.warning())) {
            data.put("warning", imageResult.warning());
        }
        data.put("message", "ok");
        return data;
    }

    private PromptEnhancementResult tryEnhancePromptWithUserTextModel(
            UserModelOverride override,
            String prompt,
            String imageModelName,
            String imageProviderSource,
            String referencePolicy
    ) {
        try {
            String enhancedPrompt = enhancePromptWithUserTextModel(
                    override,
                    prompt,
                    imageModelName,
                    imageProviderSource,
                    referencePolicy
            );
            return new PromptEnhancementResult(enhancedPrompt, true, "");
        } catch (BusinessException ex) {
            log.warn(
                    "image prompt enhancement skipped: provider={} textModel={} imageProvider={} imageModel={} msg={}",
                    safe(override == null ? null : override.providerSource()),
                    safe(override == null ? null : override.textModelOrFallback()),
                    safe(imageProviderSource),
                    safe(imageModelName),
                    safe(ex.getMessage())
            );
            return new PromptEnhancementResult(safe(prompt), false, promptEnhancementSkippedWarning());
        } catch (Exception ex) {
            log.warn(
                    "image prompt enhancement failed: provider={} textModel={} imageProvider={} imageModel={}",
                    safe(override == null ? null : override.providerSource()),
                    safe(override == null ? null : override.textModelOrFallback()),
                    safe(imageProviderSource),
                    safe(imageModelName),
                    ex
            );
            return new PromptEnhancementResult(safe(prompt), false, promptEnhancementSkippedWarning());
        }
    }

    private String enhancePromptWithUserTextModel(
            UserModelOverride override,
            String prompt,
            String imageModelName,
            String imageProviderSource,
            String referencePolicy
    ) {
        String fallbackPrompt = safe(prompt);
        if (override == null || !StringUtils.hasText(fallbackPrompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图提示词为空，请重新输入生图需求");
        }
        String providerSource = safe(override.providerSource());
        String modelName = safe(override.textModelOrFallback());
        String apiKey = safe(override.apiKey());
        String customUrl = safe(override.customUrl());
        if (!StringUtils.hasText(providerSource) || !StringUtils.hasText(modelName) || !StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先在 AI 设置中补全聊天模型和 API Key，生图需要先用它增强提示词");
        }
        try {
            String baseUrl = userAiProviderService.resolveProviderBaseUrlForSource(providerSource, customUrl);
            if (!StringUtils.hasText(baseUrl)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前聊天模型平台地址不可用，请检查 AI 设置");
            }

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("temperature", 0.35d);
            body.put("max_tokens", 900);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", imagePromptEnhancerSystemPrompt());
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", imagePromptEnhancerUserPrompt(fallbackPrompt, imageModelName, imageProviderSource, referencePolicy));

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(PROMPT_ENHANCE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String enhancedPrompt = trim(extractChatCompletionText(root), 3200);
            if (!StringUtils.hasText(enhancedPrompt) || enhancedPrompt.length() < 24) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "提示词增强没有返回有效内容，请换一个聊天模型或稍后重试");
            }
            return enhancedPrompt;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "提示词增强失败，请检查聊天模型配置或稍后重试");
        }
    }

    private static String imagePromptEnhancerSystemPrompt() {
        return """
                You are an image prompt director for anime, game, and character art.
                Convert the user's structured brief into one strong final image prompt.
                Preserve character identity, user intent, named characters, and explicit scene requirements.
                Write concrete visual details: subject, face, hair, outfit, pose, emotion, camera, composition, background, lighting, mood, style, and quality.
                Keep reference-image instructions if present: use the reference for identity, not for copying pose/background/composition.
                Return only the final image prompt. Do not use markdown, JSON, explanations, or safety commentary.
                """;
    }

    private static String imagePromptEnhancerUserPrompt(
            String prompt,
            String imageModelName,
            String imageProviderSource,
            String referencePolicy
    ) {
        return """
                Target image provider: %s
                Target image model: %s
                Reference policy: %s

                Source brief:
                %s

                Final prompt requirements:
                - Prefer English visual prompt terms, but preserve Chinese names, character names, and user-specified words when they matter.
                - Keep it concise enough for image models, but detailed enough to guide composition and identity.
                - Include a negative prompt section at the end if the source brief contains one.
                """.formatted(
                firstNonBlank(imageProviderSource, "unknown"),
                firstNonBlank(imageModelName, "unknown"),
                firstNonBlank(referencePolicy, "prompt_first"),
                safe(prompt)
        );
    }

    private static String extractChatCompletionText(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode first = choices.get(0);
            String messageText = text(first.path("message").path("content"));
            if (StringUtils.hasText(messageText)) {
                return stripPromptWrappers(messageText);
            }
            String directText = text(first.path("text"));
            if (StringUtils.hasText(directText)) {
                return stripPromptWrappers(directText);
            }
        }
        String outputText = text(root.path("output_text"));
        if (StringUtils.hasText(outputText)) {
            return stripPromptWrappers(outputText);
        }
        return "";
    }

    private static String stripPromptWrappers(String value) {
        String text = safe(value).trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = text.lastIndexOf("```");
            if (end >= 0) {
                text = text.substring(0, end);
            }
        }
        return text.trim();
    }

    private ReferenceImage resolveReferenceImage(
            String clientUid,
            long characterId,
            String referenceImageUrl,
            String requestOrigin
    ) {
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
        return fetchReferenceImage(referenceImageUrl, requestOrigin);
    }

    private ReferenceImage fetchCharacterReferenceImage(String clientUid, long characterId, String requestOrigin) {
        if (characterId <= 0) {
            return null;
        }
        AppCharacter character = characterMapper.findById(characterId);
        if (character == null || character.getDeletedAt() != null) {
            return null;
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            AppUser user = entitlementService.resolveUser(clientUid);
            if (ownerId == null || !ownerId.equals(user.getId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
            }
        } else {
            if (Boolean.FALSE.equals(character.getClientVisible())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
            }
            H5EntitlementService.CharacterAccess access =
                    entitlementService.resolveCharacterAccess(clientUid, character.getVipOnly(), character.getUnlockedDefault());
            if (!access.unlocked()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, firstNonBlank(access.lockReason(), "角色参考图不可访问"));
            }
        }

        String[] candidates = new String[] {
                absolutizeTrustedReferenceUrl(
                        stAssetUrls.resolveWithPreset(character.getStAvatarUrl(), "detail"),
                        requestOrigin
                ),
                absolutizeTrustedReferenceUrl(
                        stAssetUrls.resolve(character.getStAvatarUrl()),
                        requestOrigin
                ),
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

    private ProviderImageResult requestImagePromptAware(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count,
            ReferenceImage referenceImage,
            String referencePolicy,
            String initialWarning
    ) {
        if (!shouldAttemptReferenceImage(referencePolicy) || referenceImage == null) {
            if ("siliconflow".equals(providerSource)) {
                return requestSiliconFlowTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count)
                        .withWarning(initialWarning);
            }
            if ("openrouter".equals(providerSource)) {
                return requestOpenRouterTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio)
                        .withWarning(initialWarning);
            }
            return requestTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count)
                    .withWarning(initialWarning);
        }
        try {
            return requestImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count, referenceImage)
                    .withReferenceApplied(true)
                    .withWarning(initialWarning);
        } catch (BusinessException ex) {
            if (!shouldFallbackToWeakConsistency(referencePolicy, ex)) {
                throw ex;
            }
            String fallbackWarning = weakConsistencyWarning("当前模型不支持参考图");
            String finalWarning = firstNonBlank(initialWarning, fallbackWarning);
            if ("siliconflow".equals(providerSource)) {
                return requestSiliconFlowTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count)
                        .withWarning(finalWarning);
            }
            if ("openrouter".equals(providerSource)) {
                return requestOpenRouterTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio)
                        .withWarning(finalWarning);
            }
            return requestTextToImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count)
                    .withWarning(finalWarning);
        }
    }

    private ProviderImageResult requestSiliconFlowTextToImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("prompt", prompt);
        body.put("image_size", aspectRatioToSiliconFlowSize(aspectRatio));
        if (count > 1) {
            body.put("batch_size", count);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图请求组装失败");
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台没有返回图片");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回了无法识别的图片格式");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图请求失败，请稍后重试");
        }
    }

    private ProviderImageResult requestOpenRouterTextToImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("stream", false);
        ArrayNode modalities = body.putArray("modalities");
        modalities.add("image");
        modalities.add("text");
        ObjectNode imageConfig = body.putObject("image_config");
        imageConfig.put("aspect_ratio", aspectRatioToOpenRouter(aspectRatio));

        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8));
        builder.header("X-Title", "Clover Tavern");

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台没有返回图片");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回了无法识别的图片格式");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图请求失败，请稍后重试");
        }
    }

    private ProviderImageResult requestImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count,
            ReferenceImage referenceImage
    ) {
        if (referenceImage == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "本次生图需要参考图");
        }
        if ("siliconflow".equals(providerSource)) {
            return requestSiliconFlowImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count, referenceImage);
        }
        if ("openrouter".equals(providerSource)) {
            return requestOpenRouterImage(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, referenceImage);
        }
        try {
            return requestImageEdit(providerSource, baseUrl, apiKey, modelName, prompt, aspectRatio, count, referenceImage);
        } catch (BusinessException ex) {
            if (shouldFailAsUnsupportedReferenceEdit(ex)) {
                throw new BusinessException(
                        ErrorCode.UPSTREAM_ERROR,
                        "当前平台或模型不支持参考图生图，请切换到支持 image edit、img2img 或 reference edit 的模型"
                );
            }
            throw ex;
        }
    }

    private ProviderImageResult requestSiliconFlowImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count,
            ReferenceImage referenceImage
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("prompt", prompt);
        body.put("image_size", aspectRatioToSiliconFlowSize(aspectRatio));
        body.put("image", encodeReferenceImageAsDataUrl(referenceImage));
        if (count > 1) {
            body.put("batch_size", count);
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图请求组装失败");
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u6ca1\u6709\u8fd4\u56de\u56fe\u7247");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u8fd4\u56de\u4e86\u65e0\u6cd5\u8bc6\u522b\u7684\u56fe\u7247\u683c\u5f0f");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
        }
    }

    private ProviderImageResult requestOpenRouterImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            ReferenceImage referenceImage
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("stream", false);
        ArrayNode modalities = body.putArray("modalities");
        modalities.add("image");
        modalities.add("text");
        ObjectNode imageConfig = body.putObject("image_config");
        imageConfig.put("aspect_ratio", aspectRatioToOpenRouter(aspectRatio));

        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        imagePart.putObject("image_url").put("url", encodeReferenceImageAsDataUrl(referenceImage));

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8));
        builder.header("X-Title", "Clover Tavern");

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u6ca1\u6709\u8fd4\u56de\u56fe\u7247");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u8fd4\u56de\u4e86\u65e0\u6cd5\u8bc6\u522b\u7684\u56fe\u7247\u683c\u5f0f");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "\u53c2\u8003\u56fe\u751f\u56fe\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
        }
    }

    private ProviderImageResult requestTextToImage(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("prompt", prompt);
        body.put("n", Math.max(1, count));
        body.put("size", aspectRatioToSize(aspectRatio));
        body.put("response_format", "b64_json");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/generations"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图编辑请求组装失败");
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "参考图生图没有返回图片");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "参考图生图返回了无法识别的图片格式");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "参考图生图请求失败，请稍后重试");
        }
    }

    private ProviderImageResult requestImageEdit(
            String providerSource,
            String baseUrl,
            String apiKey,
            String modelName,
            String prompt,
            String aspectRatio,
            int count,
            ReferenceImage referenceImage
    ) {
        String boundary = "----SillyBoundary" + System.currentTimeMillis();
        byte[] bodyBytes = buildMultipartEditBody(boundary, modelName, prompt, aspectRatio, count, referenceImage);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/images/edits"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw providerError(response.body(), providerSource, response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = firstImageNode(root);
            if (first == null || first.isMissingNode() || first.isNull()) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台没有为参考图请求返回图片");
            }
            String dataUrl = resolveProviderImageDataUrl(first);
            if (!StringUtils.hasText(dataUrl)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台为参考图请求返回了无法识别的图片格式");
            }
            int[] size = aspectRatioToDimensions(aspectRatio);
            return new ProviderImageResult(dataUrl, size[0], size[1]);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台为参考图请求返回了无法识别的图片格式");
        }
    }

    private byte[] buildMultipartEditBody(
            String boundary,
            String modelName,
            String prompt,
            String aspectRatio,
            int count,
            ReferenceImage referenceImage
    ) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeMultipartText(output, boundary, "model", modelName);
            writeMultipartText(output, boundary, "prompt", prompt);
            writeMultipartText(output, boundary, "n", String.valueOf(Math.max(1, count)));
            writeMultipartText(output, boundary, "size", aspectRatioToSize(aspectRatio));
            writeMultipartText(output, boundary, "response_format", "b64_json");
            writeMultipartFile(output, boundary, "image", referenceImage.fileName(), referenceImage.contentType(), referenceImage.bytes());
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考图生图请求组装失败");
        }
    }

    private static void writeMultipartText(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(safe(value).getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeMultipartFile(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String fileName,
            String contentType,
            byte[] bytes
    ) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + safe(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + firstNonBlank(contentType, "image/png") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes == null ? new byte[0] : bytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String resolveProviderImageDataUrl(JsonNode imageNode) throws IOException, InterruptedException {
        String b64 = firstNonBlank(
                text(imageNode.get("b64_json")),
                text(imageNode.get("b64")),
                text(imageNode.get("image_base64")),
                text(imageNode.get("base64"))
        );
        if (StringUtils.hasText(b64)) {
            return "data:image/png;base64," + b64.trim();
        }
        String url = firstNonBlank(
                text(imageNode.get("url")),
                text(imageNode.get("image_url")),
                text(imageNode.path("image_url").path("url")),
                text(imageNode.path("imageUrl").path("url"))
        );
        if (!StringUtils.hasText(url)) {
            return "";
        }
        if (url.startsWith("data:")) {
            return url;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().length == 0) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回了无法读取的图片地址");
        }
        String mimeType = firstNonBlank(response.headers().firstValue("Content-Type").orElse(""), "image/png");
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(response.body());
    }

    private JsonNode firstImageNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            return data.get(0);
        }
        JsonNode images = root.path("images");
        if (images.isArray() && images.size() > 0) {
            return images.get(0);
        }
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).path("message");
            JsonNode messageImages = message.path("images");
            if (messageImages.isArray() && messageImages.size() > 0) {
                return messageImages.get(0);
            }
        }
        return null;
    }

    private BusinessException providerError(String body, String providerSource, int statusCode) {
        String message = "";
        try {
            JsonNode root = objectMapper.readTree(body == null ? "" : body);
            message = firstNonBlank(
                    text(root.path("error").path("message")),
                    text(root.path("message")),
                    text(root.path("msg"))
            );
        } catch (Exception ignored) {
            message = "";
        }
        if (containsMissingImageKey(message)) {
            message = "当前生图模型需要可读取的参考图。请确认角色卡图片可访问，或切换到支持 image edit、img2img 或 reference edit 的模型。";
        }
        if (!StringUtils.hasText(message)) {
            message = "生图平台请求失败（" + providerSource + "，" + statusCode + "）";
        }
        String lower = safe(message).toLowerCase();
        if (lower.contains("not available in your region")
                || lower.contains("unsupported country")
                || lower.contains("unsupported region")) {
            message = "\u5f53\u524d\u751f\u56fe\u6a21\u578b\u5728\u4f60\u6240\u5728\u5730\u533a\u4e0d\u53ef\u7528\uff0c\u8bf7\u5207\u6362\u5176\u4ed6\u751f\u56fe\u6a21\u578b";
        }
        return new BusinessException(ErrorCode.UPSTREAM_ERROR, message);
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
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().length == 0) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "角色参考图获取失败");
            }
            String contentType = firstNonBlank(response.headers().firstValue("Content-Type").orElse(""), "image/png");
            return new ReferenceImage(response.body(), contentType, guessFileNameByContentType(contentType));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "角色参考图获取失败");
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

    private static boolean shouldFailAsUnsupportedReferenceEdit(BusinessException ex) {
        String message = safe(ex.getMessage()).toLowerCase();
        return message.contains("not found")
                || message.contains("unknown url")
                || message.contains("unsupported")
                || message.contains("not support")
                || message.contains("does not support images")
                || message.contains("image_url")
                || message.contains("modalities")
                || message.contains("vision")
                || message.contains("not implemented")
                || message.contains("method not allowed")
                || message.contains("/images/edits");
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
            return new ReferenceImage(bytes, contentType, guessFileNameByContentType(contentType));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色参考图数据解码失败");
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

    private static int normalizePort(URI uri) {
        if (uri == null) {
            return -1;
        }
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String aspectRatioToSize(String aspectRatio) {
        return switch (safe(aspectRatio)) {
            case "square" -> "1024x1024";
            case "landscape", "wide" -> "1536x1024";
            default -> "1024x1536";
        };
    }

    private static int[] aspectRatioToDimensions(String aspectRatio) {
        return switch (safe(aspectRatio)) {
            case "square" -> new int[] {1024, 1024};
            case "landscape", "wide" -> new int[] {1536, 1024};
            default -> new int[] {1024, 1536};
        };
    }

    private static String aspectRatioToSiliconFlowSize(String aspectRatio) {
        return switch (safe(aspectRatio)) {
            case "square" -> "512x512";
            case "landscape", "wide" -> "1024x576";
            default -> "768x1024";
        };
    }

    private static String aspectRatioToOpenRouter(String aspectRatio) {
        return switch (safe(aspectRatio)) {
            case "square" -> "1:1";
            case "landscape", "wide" -> "16:9";
            default -> "3:4";
        };
    }

    private static String normalizeAspectRatio(Object value) {
        String safe = safe(value);
        if ("square".equals(safe) || "landscape".equals(safe) || "wide".equals(safe)) {
            return safe;
        }
        return "portrait";
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
        String text = String.valueOf(value == null ? "" : value).replace("\r", " ").replace("\n", "\n").trim();
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

    private String encodeReferenceImageAsDataUrl(ReferenceImage referenceImage) {
        if (referenceImage == null || referenceImage.bytes() == null || referenceImage.bytes().length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u89d2\u8272\u53c2\u8003\u56fe\u4e0d\u53ef\u7528");
        }
        String mimeType = firstNonBlank(referenceImage.contentType(), "image/png");
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(referenceImage.bytes());
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "\u751f\u56fe\u8bf7\u6c42\u7ec4\u88c5\u5931\u8d25");
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String normalizeImageBaseUrl(String providerSource, String baseUrl) {
        String safeBaseUrl = safe(baseUrl);
        if (!StringUtils.hasText(safeBaseUrl)) {
            return safeBaseUrl;
        }
        return safeBaseUrl;
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
        if ("reference_only".equals(policy)) {
            return true;
        }
        return "balanced".equals(policy) || "auto".equals(policy);
    }

    private static boolean shouldFallbackToWeakConsistency(String referencePolicy, BusinessException ex) {
        String policy = safe(referencePolicy).toLowerCase();
        if ("prompt_first".equals(policy)) {
            return false;
        }
        return shouldFailAsUnsupportedReferenceEdit(ex);
    }

    private static String weakConsistencyWarning(String reason) {
        /*
        legacy corrupted text removed during repair
        reference warning text repaired
        */
        String prefix = StringUtils.hasText(reason) ? reason + "\uFF0C" : "";
        return prefix + "\u5f53\u524d\u6a21\u578b\u53ea\u80fd\u505a\u5f31\u4e00\u81f4\u6027\uff0c\u53ef\u80fd\u4e0d\u50cf\u540c\u4e00\u4e2a\u89d2\u8272";
    }

    private static String promptEnhancementSkippedWarning() {
        return "\u63d0\u793a\u8bcd\u589e\u5f3a\u6682\u4e0d\u53ef\u7528\uff0c\u5df2\u4f7f\u7528\u539f\u59cb\u63d0\u793a\u8bcd\u7ee7\u7eed\u751f\u56fe";
    }

    private static String mergeWarnings(String... warnings) {
        if (warnings == null) {
            return "";
        }
        StringBuilder merged = new StringBuilder();
        for (String warning : warnings) {
            String text = safe(warning);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (merged.indexOf(text) >= 0) {
                continue;
            }
            if (!merged.isEmpty()) {
                merged.append("\uff1b");
            }
            merged.append(text);
        }
        return merged.toString();
    }

    private static String managedProviderErrorMessage(String message) {
        String text = safe(message);
        String lower = text.toLowerCase();
        if (lower.contains("unauthorized")
                || lower.contains("forbidden")
                || lower.contains("invalid api key")
                || lower.contains("api key")
                || lower.contains("401")
                || lower.contains("403")) {
            return "生图服务鉴权失败，请联系管理员检查配置";
        }
        if (lower.contains("rate limit")
                || lower.contains("too many requests")
                || lower.contains("429")
                || lower.contains("timeout")
                || lower.contains("timed out")) {
            return "生图服务繁忙，请稍后重试";
        }
        if (lower.contains("not available in your region")
                || lower.contains("unsupported country")
                || lower.contains("unsupported region")) {
            return "当前生图模型在服务所在地区不可用，请联系管理员切换模型";
        }
        if (lower.contains("siliconflow")
                || lower.contains("openrouter")
                || lower.contains("openai")
                || lower.contains("comfy")
                || lower.contains("sillytavern")
                || lower.contains("checkpoint")
                || lower.contains("workflow")) {
            return "生图服务处理失败，请稍后重试或联系管理员";
        }
        return StringUtils.hasText(text) ? text : "生图服务处理失败，请稍后重试";
    }

    private static boolean looksLikeImageGenerationModel(String modelName) {
        String text = safe(modelName).toLowerCase();
        return StringUtils.hasText(text);
    }

    private static boolean looksLikeReferenceEditModel(String modelName) {
        String text = safe(modelName).toLowerCase();
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.matches(".*(image-?edit|img2img|image-to-image|image2image|inpaint|outpaint|controlnet|variation|variations|reference|remix|repaint|paint-by-example|kontext).*");
    }

    private static boolean containsMissingImageKey(String message) {
        String text = safe(message).toLowerCase();
        return text.contains("missing required key: image")
                || text.contains("missing required parameter: image")
                || text.contains("required key: image")
                || text.contains("required parameter: image");
    }

    private static String guessFileNameByContentType(String contentType) {
        String text = safe(contentType).toLowerCase();
        if (text.contains("webp")) return "reference.webp";
        if (text.contains("jpeg") || text.contains("jpg")) return "reference.jpg";
        if (text.contains("gif")) return "reference.gif";
        return "reference.png";
    }

    private record ProviderImageResult(String dataUrl, int width, int height, boolean referenceApplied, String warning) {
        private ProviderImageResult(String dataUrl, int width, int height) {
            this(dataUrl, width, height, false, "");
        }

        private ProviderImageResult withReferenceApplied(boolean nextReferenceApplied) {
            return new ProviderImageResult(dataUrl, width, height, nextReferenceApplied, warning);
        }

        private ProviderImageResult withWarning(String nextWarning) {
            return new ProviderImageResult(dataUrl, width, height, referenceApplied, safe(nextWarning));
        }
    }

    private record PromptEnhancementResult(String prompt, boolean enhanced, String warning) {}

    private record ReferenceImage(byte[] bytes, String contentType, String fileName) {}
}
