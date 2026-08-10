package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiProviderCallException;
import com.example.sillyspringboot.ai.service.AiProviderFailurePolicy;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterSummary;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import com.example.sillyspringboot.integration.sillytavern.dto.OpenRouterGenerationAdminDto;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;

/**
 * 閸烆垯绔撮崗浣筋啅閸欐垼鎹?ST 閸樼喎顫?HTTP 鐠嬪啰鏁ら惃鍕閿涙稖鐭惧鍕矌瀵洜鏁?{@link StApiPaths}閵? */
public final class StClient {

    private static final Logger log = LoggerFactory.getLogger(StClient.class);
    private static final Pattern VISION_SPECIAL_TOKEN_PATTERN = Pattern.compile("<\\|[A-Za-z0-9_:-]+\\|>");

    private static final String CSRF_HEADER = "X-CSRF-Token";
    /** ST 閸忔娊妫?CSRF 閺?{@code /csrf-token} 鏉╂柨娲栭惃鍕窗娴ｅ稄绱濋崘鍛村劥娑撳秶鏁ょ拠銉よ娴ｆ粏顕Ч鍌氥仈 */
    private static final String CSRF_TOKEN_DISABLED = "\u0000csrf_disabled\u0000";

    private final RestClient restClient;
    private final HttpClient stHttpClient;
    private final SillyTavernProperties properties;
    private final OpenRouterGenerationSettingsService generationSettingsService;
    private final StModelRoutingService modelRoutingService;
    private final AiRoutingService aiRoutingService;
    private final StGenerateBodyCapture generateBodyCapture;
    private final StRuntimeChatWriteCapture runtimeChatWriteCapture;
    private final GenerationTelemetryService generationTelemetryService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object csrfLock = new Object();
    private volatile String csrfTokenCache;

    /** 娑撳孩绁荤憴鍫濇珤瑜版挸澧?ST settings.json 閸氬本顒為惃?OAI 閸欏倹鏆熺紓鎾崇摠閿涘矂浼╅崗宥囩秹閸忓磭鏁ら柨?chat_completion_source / model */
    private volatile StOaiRuntime oaiRuntimeCache;
    private volatile long oaiRuntimeCacheAtMs;
    /**
     * ST settings.json 閸欘垵鍏橀崷銊ㄧ箥閽€銉︽埂妫版垹绠掔拫鍐╂殻閿涙稓绱︾€涙绻冮梹澶哥窗闁姵鍨氶妴宀€缍夐崗鍐插棘閺佺増绮搁崥搴涒偓宥囨畱娴ｆ挻鍔呴妴?     * 娣囨繃瀵旀潏鍐叚 TTL閿涘本妫﹂崙蹇撶毌 ST 閸樺濮忛敍灞肩瘍閼宠棄鎻╅柅鐔荤闂呭繒缍夋い鐢殿伂鐠嬪啫寮妴?     */
    private static final long OAI_RUNTIME_CACHE_MS = 5_000L;

    private record StOaiRuntime(
            String chatCompletionSource,
            String model,
            String reverseProxy,
            String proxyPassword,
            String reasoningEffort,
            boolean includeReasoning,
            String customUrl,
            // --- ST preset parameters (from settings.json) ---
            Double tempOpenai,
            Integer openaiMaxTokens,
            Double topPOpenai,
            Integer topKOpenai,
            Double minPOpenai,
            Double topAOpenai,
            Double freqPenOpenai,
            Double presPenOpenai,
            Double repetitionPenaltyOpenai,
            String openrouterMiddleout,
            Boolean openrouterAllowFallbacks,
            String verbosity
    ) {

        static StOaiRuntime fromYaml(SillyTavernProperties p) {
            return new StOaiRuntime(
                    p.getChatCompletionSource() == null || p.getChatCompletionSource().isBlank()
                            ? "openai"
                            : p.getChatCompletionSource().trim(),
                    p.getDefaultModel() == null ? "" : p.getDefaultModel().trim(),
                    p.getReverseProxy() == null ? "" : p.getReverseProxy().trim(),
                    p.getProxyPassword() == null ? "" : p.getProxyPassword().trim(),
                    p.getReasoningEffort() == null ? "" : p.getReasoningEffort().trim(),
                    p.isIncludeReasoning(),
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "",
                    null,
                    "");
        }
    }

    private record RuntimeProviderOverride(
            String providerKey,
            String displayName,
            String sceneKey,
            String chatCompletionSource,
            String model,
            String reverseProxy,
            String proxyPassword,
            String customUrl,
            Long aiDeploymentId,
            Integer requestTimeoutSeconds
    ) {
    }

    public StClient(
            SillyTavernProperties properties,
            OpenRouterGenerationSettingsService generationSettingsService,
            StModelRoutingService modelRoutingService,
            StGenerateBodyCapture generateBodyCapture,
            StRuntimeChatWriteCapture runtimeChatWriteCapture
    ) {
        this(
                properties,
                generationSettingsService,
                modelRoutingService,
                generateBodyCapture,
                runtimeChatWriteCapture,
                null,
                null
        );
    }

    public StClient(
            SillyTavernProperties properties,
            OpenRouterGenerationSettingsService generationSettingsService,
            StModelRoutingService modelRoutingService,
            StGenerateBodyCapture generateBodyCapture,
            StRuntimeChatWriteCapture runtimeChatWriteCapture,
            GenerationTelemetryService generationTelemetryService
    ) {
        this(
                properties,
                generationSettingsService,
                modelRoutingService,
                generateBodyCapture,
                runtimeChatWriteCapture,
                generationTelemetryService,
                null
        );
    }

    public StClient(
            SillyTavernProperties properties,
            OpenRouterGenerationSettingsService generationSettingsService,
            StModelRoutingService modelRoutingService,
            StGenerateBodyCapture generateBodyCapture,
            StRuntimeChatWriteCapture runtimeChatWriteCapture,
            GenerationTelemetryService generationTelemetryService,
            AiRoutingService aiRoutingService
    ) {
        this.properties = properties;
        this.generationSettingsService = generationSettingsService;
        this.modelRoutingService = modelRoutingService;
        this.aiRoutingService = aiRoutingService;
        this.generateBodyCapture = generateBodyCapture;
        this.runtimeChatWriteCapture = runtimeChatWriteCapture;
        this.generationTelemetryService = generationTelemetryService;
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.stHttpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .cookieHandler(cookieManager)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(this.stHttpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        RestClient.Builder builder =
                RestClient.builder().baseUrl(properties.getBaseUrl().toString()).requestFactory(factory);
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        this.restClient = builder.build();
    }

    /**
     * ST 閸︺劌绱戦崥?CSRF 閺冩儼顩﹀Ч鍌︾窗cookie-session + POST/SSE 閹煎搫鐢?{@code X-CSRF-Token}閵?     * 濞村繗顫嶉崳銊ㄥ殰閸斻劌鐣幋鎰剁幢缂冩垵鍙ф笟褔娓堕崗?GET {@code /csrf-token}閿涘牅绗岄崥搴ｇ敾鐠囬攱鐪伴崗杈╂暏 Cookie 缂冩劧绱氶妴?     */
    private void ensureCsrfForMutatingRequest() {
        if (CSRF_TOKEN_DISABLED.equals(csrfTokenCache)) {
            return;
        }
        if (csrfTokenCache != null) {
            return;
        }
        synchronized (csrfLock) {
            if (CSRF_TOKEN_DISABLED.equals(csrfTokenCache)) {
                return;
            }
            if (csrfTokenCache != null) {
                return;
            }
            JsonNode node;
            try {
                // Spring Boot 4 / RestClient 娴ｈ法鏁?Jackson 3 閸欏秴绨崚妤€瀵查崳顭掔礉娑撳秷鍏橀惄瀛樺复閹跺﹤鎼锋惔鏂剧秼缂佹垵鍩?
                String raw = restClient.get()
                        .uri("/csrf-token")
                        .retrieve()
                        .body(String.class);
                node = raw == null || raw.isBlank() ? null : objectMapper.readTree(raw);
            } catch (RestClientException e) {
                throw new StUnavailableException(e);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new StUnavailableException(e);
            }
            if (node == null || !node.has("token")) {
                throw new StUnavailableException(new IllegalStateException("st csrf-token response missing token"));
            }
            String token = node.path("token").asText("");
            if ("disabled".equals(token)) {
                csrfTokenCache = CSRF_TOKEN_DISABLED;
            } else if (!StringUtils.hasText(token)) {
                throw new StUnavailableException(new IllegalStateException("st csrf-token empty"));
            } else {
                csrfTokenCache = token;
            }
        }
    }

    private void applyCsrfHeader(HttpRequest.Builder b) {
        ensureCsrfForMutatingRequest();
        if (!CSRF_TOKEN_DISABLED.equals(csrfTokenCache)) {
            b.header(CSRF_HEADER, csrfTokenCache);
        }
    }

    private RestClient.RequestBodySpec postSt(String path) {
        ensureCsrfForMutatingRequest();
        return restClient.post()
                .uri(path)
                .httpRequest(hr -> {
                    if (!CSRF_TOKEN_DISABLED.equals(csrfTokenCache)) {
                        hr.getHeaders().add(CSRF_HEADER, csrfTokenCache);
                    }
                });
    }

    public String loadComfyWorkflow(String fileName) {
        try {
            String raw = postSt(StApiPaths.SD_COMFY_WORKFLOW)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("file_name", fileName == null ? "" : fileName))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                throw new StUnavailableException(new IllegalStateException("empty comfy workflow response"));
            }
            JsonNode root = objectMapper.readTree(raw);
            return root.isTextual() ? root.asText("") : raw;
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException | JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
    }

    public String generateComfyImage(Map<String, Object> body) {
        try {
            return postSt(StApiPaths.SD_COMFY_GENERATE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? Map.of() : body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    /**
     * 閹恒垺绁?ST 鏉╂稓鈻?HTTP 閺勵垰鎯侀崣顖濇彧閿涙艾顕弽纭呯熅瀵板嫬褰傜挧?GET閿涘苯鐨?404 鐟欏棔璐熼妴宀冪箻缁嬪鍑￠崫宥呯安閵嗗秲鈧?     */
    public void checkConnectivity() {
        try {
            restClient.get().uri("/").retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    /**
     * 閹峰褰?ST 閻劍鍩涢惄顔肩秿闂堟瑦鈧浇绁┃鎰剁礄婵?{@code /characters/*.png}閿涘鈧倿娓跺鎻掔紦缁?session閿涘牏鏁?{@link #ensureCsrfForMutatingRequest} 鐎瑰本鍨氶敍澶堚偓?     *
     * @param absolutePath 韫囧懘銆忔禒?{@code /} 瀵偓婢惰揪绱濇稉鏂剧瑝瀵版瀵橀崥?{@code ..}
     * @return 404 閺冩儼绻戦崶?null
     */
    public byte[] fetchUserDirectoryFile(String absolutePath) {
        if (absolutePath == null || absolutePath.isBlank()) {
            throw new IllegalArgumentException("path");
        }
        String p = absolutePath.trim();
        if (!p.startsWith("/") || p.indexOf("..") >= 0) {
            throw new IllegalArgumentException("path");
        }
        ensureCsrfForMutatingRequest();
        try {
            return restClient.get().uri(p).retrieve().body(byte[].class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private StOaiRuntime cachedStOaiRuntime() {
        long now = System.currentTimeMillis();
        StOaiRuntime c = oaiRuntimeCache;
        if (c != null && now - oaiRuntimeCacheAtMs < OAI_RUNTIME_CACHE_MS) {
            return c;
        }
        synchronized (csrfLock) {
            if (oaiRuntimeCache != null && now - oaiRuntimeCacheAtMs < OAI_RUNTIME_CACHE_MS) {
                return oaiRuntimeCache;
            }
            StOaiRuntime loaded = loadOaiRuntimeFromStSettings();
            if (loaded == null) {
                loaded = StOaiRuntime.fromYaml(properties);
            }
            oaiRuntimeCache = loaded;
            oaiRuntimeCacheAtMs = System.currentTimeMillis();
            return loaded;
        }
    }

    private StOaiRuntime loadOaiRuntimeFromStSettings() {
        try {
            ObjectNode root = readStSettingsRoot();
            JsonNode s = resolveOaiSettingsNode(root);
            String source = firstNonBlank(
                    textNode(s, "chat_completion_source"),
                    properties.getChatCompletionSource(),
                    "openai");
            String modelField = modelFieldForStSource(source);
            String model = "";
            if (modelField != null && !modelField.isBlank()) {
                model = textNode(s, modelField);
            }
            if (model.isBlank()) {
                model = textNode(s, "openai_model");
            }
            String rev = textNode(s, "reverse_proxy");
            String pp = textNode(s, "proxy_password");
            JsonNode reNode = s.get("reasoning_effort");
            String re =
                    reNode == null || reNode.isNull()
                            ? ""
                            : (reNode.isTextual()
                                    ? reNode.asText("")
                                    : reNode.toString().replace("\"", ""));
            boolean thoughts = s.path("show_thoughts").asBoolean(true);
            String customUrl = textNode(s, "custom_url");
            Double tempOpenai = root.has("temp_openai") ? root.path("temp_openai").asDouble() : null;
            Integer openaiMaxTokens = root.has("openai_max_tokens") ? root.path("openai_max_tokens").asInt() : null;
            Double topPOpenai = root.has("top_p_openai") ? root.path("top_p_openai").asDouble() : null;
            Integer topKOpenai = root.has("top_k_openai") ? root.path("top_k_openai").asInt() : null;
            Double minPOpenai = root.has("min_p_openai") ? root.path("min_p_openai").asDouble() : null;
            Double topAOpenai = root.has("top_a_openai") ? root.path("top_a_openai").asDouble() : null;
            Double freqPenOpenai = root.has("freq_pen_openai") ? root.path("freq_pen_openai").asDouble() : null;
            Double presPenOpenai = root.has("pres_pen_openai") ? root.path("pres_pen_openai").asDouble() : null;
            Double repetitionPenaltyOpenai =
                    root.has("repetition_penalty_openai") ? root.path("repetition_penalty_openai").asDouble() : null;
            String openrouterMiddleout =
                    root.has("openrouter_middleout") ? root.path("openrouter_middleout").asText("").trim() : "";
            Boolean openrouterAllowFallbacks =
                    root.has("openrouter_allow_fallbacks") ? root.path("openrouter_allow_fallbacks").asBoolean() : null;
            String verbosity =
                    root.has("verbosity_openai") ? root.path("verbosity_openai").asText("").trim() : "";
            if (verbosity.isBlank() && root.has("verbosity")) {
                verbosity = root.path("verbosity").asText("").trim();
            }
            return new StOaiRuntime(
                    source,
                    model,
                    rev,
                    pp,
                    re,
                    thoughts,
                    customUrl,
                    tempOpenai,
                    openaiMaxTokens,
                    topPOpenai,
                    topKOpenai,
                    minPOpenai,
                    topAOpenai,
                    freqPenOpenai,
                    presPenOpenai,
                    repetitionPenaltyOpenai,
                    openrouterMiddleout,
                    openrouterAllowFallbacks,
                    verbosity
            );
        } catch (Exception e) {
            return null;
        }
    }

    public OpenRouterGenerationAdminDto getGenerationSettingsForAdmin() {
        ObjectNode root = readStSettingsRoot();
        JsonNode s = resolveOaiSettingsNode(root);
        OpenRouterGenerationAdminDto dto = new OpenRouterGenerationAdminDto();
        String source = firstNonBlank(textNode(s, "chat_completion_source"), properties.getChatCompletionSource(), "openai");
        String modelField = modelFieldForStSource(source);
        String model = modelField == null ? "" : textNode(s, modelField);
        dto.setChatCompletionSource(source);
        dto.setDefaultModel(firstNonBlank(model, textNode(s, "openai_model"), properties.getDefaultModel()));
        dto.setDefaultTemperature(
                s.has("temp_openai") ? clamp(s.path("temp_openai").asDouble(0.85d), 0d, 2d)
                        : (root.has("temp_openai") ? clamp(root.path("temp_openai").asDouble(0.85d), 0d, 2d) : 0.85d));
        dto.setDefaultMaxOutputTokens(
                s.has("openai_max_tokens") ? Math.max(0, s.path("openai_max_tokens").asInt(2048))
                        : (root.has("openai_max_tokens") ? Math.max(0, root.path("openai_max_tokens").asInt(2048)) : 2048));
        dto.setMaxContextUnlocked(
                s.has("max_context_unlocked") ? s.path("max_context_unlocked").asBoolean(false)
                        : (root.has("max_context_unlocked") ? root.path("max_context_unlocked").asBoolean(false) : Boolean.FALSE));
        dto.setOpenaiMaxContext(
                s.has("openai_max_context") ? Math.max(0, s.path("openai_max_context").asInt(0))
                        : (root.has("openai_max_context") ? Math.max(0, root.path("openai_max_context").asInt(0)) : 0));
        dto.setTopP(
                s.has("top_p_openai") ? clamp(s.path("top_p_openai").asDouble(1d), 0d, 1d)
                        : (root.has("top_p_openai") ? clamp(root.path("top_p_openai").asDouble(1d), 0d, 1d) : 1d));
        dto.setTopK(
                s.has("top_k_openai") ? Math.max(0, s.path("top_k_openai").asInt(0))
                        : (root.has("top_k_openai") ? Math.max(0, root.path("top_k_openai").asInt(0)) : 0));
        dto.setMinP(
                s.has("min_p_openai") ? clamp(s.path("min_p_openai").asDouble(0d), 0d, 1d)
                        : (root.has("min_p_openai") ? clamp(root.path("min_p_openai").asDouble(0d), 0d, 1d) : 0d));
        dto.setTopA(
                s.has("top_a_openai") ? clamp(s.path("top_a_openai").asDouble(0d), 0d, 1d)
                        : (root.has("top_a_openai") ? clamp(root.path("top_a_openai").asDouble(0d), 0d, 1d) : 0d));
        dto.setFrequencyPenalty(
                s.has("freq_pen_openai") ? clamp(s.path("freq_pen_openai").asDouble(0d), -2d, 2d)
                        : (root.has("freq_pen_openai") ? clamp(root.path("freq_pen_openai").asDouble(0d), -2d, 2d) : 0d));
        dto.setPresencePenalty(
                s.has("pres_pen_openai") ? clamp(s.path("pres_pen_openai").asDouble(0d), -2d, 2d)
                        : (root.has("pres_pen_openai") ? clamp(root.path("pres_pen_openai").asDouble(0d), -2d, 2d) : 0d));
        dto.setRepetitionPenalty(
                s.has("repetition_penalty_openai") ? clamp(s.path("repetition_penalty_openai").asDouble(1d), 0d, 3d)
                        : (root.has("repetition_penalty_openai") ? clamp(root.path("repetition_penalty_openai").asDouble(1d), 0d, 3d) : 1d));
        dto.setOpenrouterMiddleout(
                s.has("openrouter_middleout") ? s.path("openrouter_middleout").asText("").trim()
                        : (root.has("openrouter_middleout") ? root.path("openrouter_middleout").asText("").trim() : ""));
        dto.setStopSequences("");
        dto.setStLinked(Boolean.TRUE);
        dto.setStError("");
        return dto;
    }

    public void saveGenerationSettingsFromAdmin(OpenRouterGenerationAdminDto body) {
        if (body == null) {
            throw new IllegalArgumentException("settings body cannot be null");
        }
        ObjectNode root = readStSettingsRoot();
        ObjectNode oaiSettings = ensureObjectNode(root, "oai_settings");
        String source = firstNonBlank(body.getChatCompletionSource(), textNode(oaiSettings, "chat_completion_source"), properties.getChatCompletionSource(), "openai");
        String model = trimmed(body.getDefaultModel());
        if (model.isBlank()) {
            model = firstNonBlank(textNode(oaiSettings, modelFieldForStSource(source)), textNode(oaiSettings, "openai_model"), properties.getDefaultModel());
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("defaultModel cannot be blank");
        }
        oaiSettings.put("chat_completion_source", source);
        root.put("chat_completion_source", source);
        String modelField = modelFieldForStSource(source);
        if (modelField != null && !modelField.isBlank()) {
            oaiSettings.put(modelField, model);
            root.put(modelField, model);
        }
        double temperature = clamp(defaultDouble(body.getDefaultTemperature(), 0.85d), 0d, 2d);
        int maxTokens = Math.max(0, defaultInt(body.getDefaultMaxOutputTokens(), 2048));
        boolean maxContextUnlocked = Boolean.TRUE.equals(body.getMaxContextUnlocked());
        int openaiMaxContext = Math.max(0, defaultInt(body.getOpenaiMaxContext(), 0));
        double topP = clamp(defaultDouble(body.getTopP(), 1d), 0d, 1d);
        int topK = Math.max(0, body.getTopK() == null
                ? (oaiSettings.has("top_k_openai") ? oaiSettings.path("top_k_openai").asInt(0) : root.path("top_k_openai").asInt(0))
                : body.getTopK());
        double minP = clamp(body.getMinP() == null
                ? (oaiSettings.has("min_p_openai") ? oaiSettings.path("min_p_openai").asDouble(0d) : root.path("min_p_openai").asDouble(0d))
                : body.getMinP(), 0d, 1d);
        double topA = clamp(body.getTopA() == null
                ? (oaiSettings.has("top_a_openai") ? oaiSettings.path("top_a_openai").asDouble(0d) : root.path("top_a_openai").asDouble(0d))
                : body.getTopA(), 0d, 1d);
        double frequencyPenalty = clamp(defaultDouble(body.getFrequencyPenalty(), 0d), -2d, 2d);
        double presencePenalty = clamp(defaultDouble(body.getPresencePenalty(), 0d), -2d, 2d);
        double repetitionPenalty = clamp(body.getRepetitionPenalty() == null
                ? (oaiSettings.has("repetition_penalty_openai")
                    ? oaiSettings.path("repetition_penalty_openai").asDouble(1d)
                    : root.path("repetition_penalty_openai").asDouble(1d))
                : body.getRepetitionPenalty(), 0d, 3d);
        String middleout = body.getOpenrouterMiddleout() == null
                ? firstNonBlank(textNode(oaiSettings, "openrouter_middleout"), textNode(root, "openrouter_middleout"))
                : trimmed(body.getOpenrouterMiddleout());
        root.put("temp_openai", temperature);
        root.put("openai_max_tokens", maxTokens);
        root.put("max_context_unlocked", maxContextUnlocked);
        root.put("openai_max_context", openaiMaxContext);
        root.put("top_p_openai", topP);
        root.put("top_k_openai", topK);
        root.put("min_p_openai", minP);
        root.put("top_a_openai", topA);
        root.put("freq_pen_openai", frequencyPenalty);
        root.put("pres_pen_openai", presencePenalty);
        root.put("repetition_penalty_openai", repetitionPenalty);
        root.put("openrouter_middleout", middleout);
        oaiSettings.put("temp_openai", temperature);
        oaiSettings.put("openai_max_tokens", maxTokens);
        oaiSettings.put("max_context_unlocked", maxContextUnlocked);
        oaiSettings.put("openai_max_context", openaiMaxContext);
        oaiSettings.put("top_p_openai", topP);
        oaiSettings.put("top_k_openai", Math.max(0, defaultInt(body.getTopK(), 0)));
        oaiSettings.put("min_p_openai", minP);
        oaiSettings.put("top_a_openai", topA);
        oaiSettings.put("freq_pen_openai", frequencyPenalty);
        oaiSettings.put("pres_pen_openai", presencePenalty);
        oaiSettings.put("repetition_penalty_openai", repetitionPenalty);
        oaiSettings.put("openrouter_middleout", middleout);
        saveStSettingsRoot(root);
        invalidateOaiRuntimeCache();
    }

    private ObjectNode readStSettingsRoot() {
        try {
            JsonNode env = readStSettingsEnvelope();
            if (env == null || !env.has("settings")) {
                throw new IllegalStateException("st settings/get missing settings field");
            }
            String settingsText = env.path("settings").asText("");
            if (settingsText.isBlank()) {
                throw new IllegalStateException("st settings payload is empty");
            }
            JsonNode root = objectMapper.readTree(settingsText);
            if (!(root instanceof ObjectNode objectNode)) {
                throw new IllegalStateException("st settings root is not object");
            }
            return objectNode;
        } catch (JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
    }

    private void saveStSettingsRoot(JsonNode root) {
        try {
            postSt(StApiPaths.SETTINGS_SAVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(root == null ? "{}" : objectMapper.writeValueAsString(root))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
    }

    private static JsonNode resolveOaiSettingsNode(JsonNode root) {
        JsonNode s = root == null ? null : root.path("oai_settings");
        if (s != null && s.isObject() && !s.isEmpty()) {
            return s;
        }
        return root == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : root;
    }

    private static ObjectNode ensureObjectNode(ObjectNode root, String fieldName) {
        JsonNode existing = root.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = root.objectNode();
        root.set(fieldName, created);
        return created;
    }

    private void invalidateOaiRuntimeCache() {
        synchronized (csrfLock) {
            oaiRuntimeCache = null;
            oaiRuntimeCacheAtMs = 0L;
        }
    }

    private static String textNode(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return "";
        }
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static double defaultDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String modelFieldForStSource(String source) {
        if (source == null || source.isBlank()) {
            return "openai_model";
        }
        return switch (source) {
            case "openai" -> "openai_model";
            case "claude" -> "claude_model";
            case "openrouter" -> "openrouter_model";
            case "ai21" -> "ai21_model";
            case "makersuite" -> "google_model";
            case "vertexai" -> "vertexai_model";
            case "mistralai" -> "mistralai_model";
            case "custom" -> "custom_model";
            case "cohere" -> "cohere_model";
            case "perplexity" -> "perplexity_model";
            case "groq" -> "groq_model";
            case "chutes" -> "chutes_model";
            case "electronhub" -> "electronhub_model";
            case "nanogpt" -> "nanogpt_model";
            case "deepseek" -> "deepseek_model";
            case "aimlapi" -> "aimlapi_model";
            case "xai" -> "xai_model";
            case "pollinations" -> "pollinations_model";
            case "moonshot" -> "moonshot_model";
            case "fireworks" -> "fireworks_model";
            case "cometapi" -> "cometapi_model";
            case "azure_openai" -> "azure_openai_model";
            case "zai" -> "zai_model";
            case "siliconflow" -> "siliconflow_model";
            default -> "openai_model";
        };
    }

    /**
     * 闂冭埖顔?4閿涙碍娓剁亸蹇撳讲閻劎娈戝ù浣哥础閻㈢喐鍨氬銉﹀复閵?     * <p>
     * 鐠囧瓨妲戦敍姘劃婢跺嫬鍘涢幐澶嗏偓娣enAI ChatCompletions 閸忕厧顔愯ぐ銏♀偓浣测偓婵囩€柅鐘侯嚞濮瑰倷缍嬮敍鍫滅矌 messages + stream閿涘绱?
     * 閸忚渹缍嬫稉?ST 閻楀牊婀伴惃鍕▕瀵倸鎮楃紒顓炲涧閸忎浇顔忛崷?StClient 鐏炲倸浠涢崗鐓庮啇鐠嬪啯鏆ｉ妴?     */
    public void streamChatCompletionsGenerate(ChatGenerateRequest request, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        if (control.isCancelled()) {
            return;
        }
        URI url = properties.getBaseUrl().resolve(StApiPaths.CHAT_COMPLETIONS_GENERATE);
        StOaiRuntime oai = cachedStOaiRuntime();
        AtomicInteger idx = new AtomicInteger(0);
        List<RuntimeProviderOverride> providerChain = resolveRuntimeProviderChain(request);
        if (providerChain.isEmpty()) {
            streamChatCompletionsGenerateAttempt(url, request, onChunk, control, oai, idx, null, 1);
            return;
        }
        StUnavailableException last = null;
        int attemptNo = 0;
        boolean bufferedVision = request != null && request.routingCapabilityOrChat() == AiCapability.VISION;
        boolean updateProviderHealth = !isAuxiliarySidecarRequest(request);
        for (RuntimeProviderOverride providerOverride : providerChain) {
            attemptNo++;
            if (control.isCancelled()) {
                return;
            }
            try {
                AtomicInteger attemptIdx = bufferedVision ? new AtomicInteger(0) : idx;
                List<ChatGenerateChunk> bufferedChunks = bufferedVision ? new java.util.ArrayList<>() : List.of();
                Consumer<ChatGenerateChunk> attemptConsumer = bufferedVision ? bufferedChunks::add : onChunk;
                streamChatCompletionsGenerateAttempt(
                        url, request, attemptConsumer, control, oai, attemptIdx, providerOverride, attemptNo);
                if (control.isCancelled()) {
                    return;
                }
                if (bufferedVision) {
                    bufferedChunks.forEach(onChunk);
                }
                if (updateProviderHealth) {
                    recordProviderSuccess(providerOverride);
                }
                return;
            } catch (StUnavailableException ex) {
                last = ex;
                AiProviderCallException providerFailure = ex.getProviderFailure();
                if (updateProviderHealth
                        && providerFailure != null
                        && AiProviderFailurePolicy.shouldCountCircuitFailure(providerFailure)) {
                    recordProviderFailure(providerOverride, providerFailure.getMessage());
                } else if (updateProviderHealth
                        && providerFailure != null
                        && !AiProviderFailurePolicy.shouldFallback(providerFailure)) {
                    recordProviderConfigurationError(providerOverride, providerFailure.getMessage());
                }
                if ((!bufferedVision && idx.get() > 0)
                        || providerOverride == null
                        || providerFailure == null
                        || !AiProviderFailurePolicy.shouldFallback(providerFailure)
                        || attemptNo >= providerChain.size()) {
                    break;
                }
                log.warn(
                        "st direct generate fallback conversationId={} providerKey={} scene={} reason={}",
                        request.conversationId(),
                        providerOverride.providerKey(),
                        providerOverride.sceneKey(),
                        providerFailure.getMessage()
                );
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private void streamChatCompletionsGenerateAttempt(
            URI url,
            ChatGenerateRequest request,
            Consumer<ChatGenerateChunk> onChunk,
            StStreamControl control,
            StOaiRuntime oai,
            AtomicInteger idx,
            RuntimeProviderOverride providerOverride,
            int attemptNo
    ) {
        AttemptTelemetry attempt = providerOverride == null
                ? startDirectAttemptTelemetry(request, oai)
                : startAttemptTelemetry(request, providerOverride, attemptNo);
        Consumer<ChatGenerateChunk> observedOnChunk = chunk -> {
            onChunk.accept(chunk);
            attempt.observe(chunk);
        };
        Exception attemptFailure = null;
        try {
            executeStreamChatCompletions(
                    url, request, observedOnChunk, control, oai, idx, true, providerOverride, attempt);
        } catch (JsonProcessingException e) {
            attemptFailure = e;
            throw new StUnavailableException(e);
        } catch (RuntimeException e) {
            attemptFailure = e;
            throw e;
        } finally {
            finishAttemptTelemetry(attempt, control.isCancelled(), attemptFailure);
        }
    }

    /**
     * StepB: let ST runtime own the final generate request body. Spring only passes runtime refs + mode.
     */
    public void streamRuntimeChatGenerate(ChatGenerateRequest request, Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        if (control.isCancelled()) {
            return;
        }
        URI url = properties.getBaseUrl().resolve(StApiPaths.RUNTIME_CHAT_GENERATE);
        AtomicInteger idx = new AtomicInteger(0);
        List<RuntimeProviderOverride> providerChain = resolveRuntimeProviderChain(request);
        if (providerChain.isEmpty()) {
            streamRuntimeChatGenerateAttempt(url, request, onChunk, control, idx, null, 1);
            return;
        }
        StUnavailableException last = null;
        int attemptNo = 0;
        boolean updateProviderHealth = !isAuxiliarySidecarRequest(request);
        for (RuntimeProviderOverride providerOverride : providerChain) {
            attemptNo++;
            if (control.isCancelled()) {
                return;
            }
            try {
                streamRuntimeChatGenerateAttempt(url, request, onChunk, control, idx, providerOverride, attemptNo);
                if (updateProviderHealth) {
                    recordProviderSuccess(providerOverride);
                }
                return;
            } catch (StUnavailableException e) {
                last = e;
                AiProviderCallException providerFailure = e.getProviderFailure();
                if (updateProviderHealth
                        && providerFailure != null
                        && AiProviderFailurePolicy.shouldCountCircuitFailure(providerFailure)) {
                    recordProviderFailure(providerOverride, providerFailure.getMessage());
                } else if (updateProviderHealth
                        && providerFailure != null
                        && !AiProviderFailurePolicy.shouldFallback(providerFailure)) {
                    recordProviderConfigurationError(providerOverride, providerFailure.getMessage());
                }
                if (idx.get() > 0
                        || providerOverride == null
                        || providerFailure == null
                        || !AiProviderFailurePolicy.shouldFallback(providerFailure)
                        || attemptNo >= providerChain.size()) {
                    break;
                }
                log.warn(
                        "st runtime generate fallback conversationId={} providerKey={} scene={} reason={}",
                        request.conversationId(),
                        providerOverride.providerKey(),
                        providerOverride.sceneKey(),
                        providerFailure.getMessage()
                );
            }
        }
        if (last != null) {
            throw last;
        }
    }

    private void streamRuntimeChatGenerateAttempt(
            URI url,
            ChatGenerateRequest request,
            Consumer<ChatGenerateChunk> onChunk,
            StStreamControl control,
            AtomicInteger idx,
            RuntimeProviderOverride providerOverride,
            int attemptNo
    ) {
        final String body;
        try {
            body = buildRuntimeChatGenerateBody(request, providerOverride);
        } catch (JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
        if (generateBodyCapture != null) {
            generateBodyCapture.capture(request.conversationId(), request.mode(), url, body);
        }
        HttpRequest httpRequest = buildChatCompletionsHttpRequest(url, body, providerOverride);
        control.addOnCancel(() -> {
            try {
                runtimeChatStop(request.stAvatarUrl(), request.stChatFileName());
            } catch (Exception ignored) {
            }
        });
        if (control.isCancelled()) {
            return;
        }

        AttemptTelemetry attempt = startAttemptTelemetry(request, providerOverride, attemptNo);
        Consumer<ChatGenerateChunk> observedOnChunk = chunk -> {
            onChunk.accept(chunk);
            attempt.observe(chunk);
        };
        Exception attemptFailure = null;
        try {
            CompletableFuture<HttpResponse<java.io.InputStream>> future =
                    stHttpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            control.addOnCancel(() -> future.cancel(true));

            HttpResponse<java.io.InputStream> resp = future.join();
            attempt.setHttpStatus(resp.statusCode());
            if (resp.statusCode() >= 400) {
                long responseBytes = discardResponseBody(resp.body());
                log.warn(
                        "st runtime generate http error conversationId={} mode={} providerKey={} routeKey={} status={} responseBytes={}",
                        request.conversationId(),
                        request.mode(),
                        providerOverride == null ? "st_default" : providerOverride.providerKey(),
                        providerOverride == null ? StModelRoutingService.DEFAULT_SCENE : providerOverride.sceneKey(),
                        resp.statusCode(),
                        responseBytes
                );
                throw providerHttpFailure("st runtime generate", resp.statusCode(), responseBytes);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                control.addOnCancel(() -> {
                    try {
                        resp.body().close();
                    } catch (Exception ignored) {
                    }
                });
                String line;
                StringBuilder dataBuf = new StringBuilder();
                boolean emittedContent = false;
                while (!control.isCancelled() && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (dataBuf.length() == 0) continue;
                        String data = dataBuf.toString().trim();
                        dataBuf.setLength(0);
                        if (data.isEmpty()) continue;
                        attempt.observeUsage(data, objectMapper);
                        StreamChunkResult result = emitStreamData(data, request, observedOnChunk, idx);
                        emittedContent = emittedContent || result.emittedContent();
                        if (result.done()) {
                            if (!emittedContent) {
                                throw providerTransientFailure("st runtime generate empty response");
                            }
                            emitDoneChunk(request, observedOnChunk, idx, null);
                            return;
                        }
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        String dataLine = line.substring(5).trim();
                        if (!dataLine.isEmpty()) {
                            if (dataBuf.length() > 0) dataBuf.append('\n');
                            dataBuf.append(dataLine);
                        }
                        continue;
                    }
                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("{") || trimmedLine.startsWith("[")) {
                        if (dataBuf.length() > 0) dataBuf.append('\n');
                        dataBuf.append(trimmedLine);
                    }
                }
                if (!control.isCancelled() && dataBuf.length() > 0) {
                    String data = dataBuf.toString().trim();
                    attempt.observeUsage(data, objectMapper);
                    StreamChunkResult result = emitStreamData(data, request, observedOnChunk, idx);
                    emittedContent = emittedContent || result.emittedContent();
                    if (result.done()) {
                        if (!emittedContent) {
                            throw providerTransientFailure("st runtime generate empty response");
                        }
                        emitDoneChunk(request, observedOnChunk, idx, null);
                        return;
                    }
                }
                if (!control.isCancelled() && !emittedContent) {
                    throw providerTransientFailure("st runtime generate empty response");
                }
            }
        } catch (Exception e) {
            attemptFailure = e;
            if (control.isCancelled()) {
                return;
            }
            throw classifyChatAttemptFailure(e, "st runtime generate transport failure");
        } finally {
            finishAttemptTelemetry(attempt, control.isCancelled(), attemptFailure);
        }
    }

    private List<RuntimeProviderOverride> resolveRuntimeProviderChain(ChatGenerateRequest request) {
        AiCapability capability = request == null ? AiCapability.CHAT : request.routingCapabilityOrChat();
        RuntimeProviderOverride userOverride = toUserRuntimeProviderOverride(
                request == null ? null : request.userModelOverride(),
                capability == AiCapability.VISION || (request != null && request.hasImageInput())
        );
        if (userOverride != null) {
            return List.of(userOverride);
        }
        if (capability == AiCapability.VISION) {
            if (aiRoutingService == null || !aiRoutingService.isCapabilityEnabled(AiCapability.VISION)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "官方识图功能当前未开放");
            }
            List<AiRoutingService.ResolvedProvider> providers = aiRoutingService.resolve(AiCapability.VISION);
            if (providers.isEmpty()) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "官方识图暂时没有可用供应商");
            }
            return providers.stream()
                    .map(item -> toV2RuntimeProviderOverride(item, AiCapability.VISION, AiCapability.VISION.defaultRouteKey()))
                    .toList();
        }
        String explicitRouteKey = request == null ? "" : request.routingRouteKeyOrEmpty();
        if (!explicitRouteKey.isBlank()) {
            if (aiRoutingService == null || !aiRoutingService.isCapabilityEnabled(AiCapability.CHAT)) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "平台聊天模型路由尚未启用");
            }
            List<AiRoutingService.ResolvedProvider> selectedProviders =
                    aiRoutingService.resolveRoute(explicitRouteKey, AiCapability.CHAT);
            if (selectedProviders.isEmpty()) {
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "所选平台模型暂时没有可用供应商");
            }
            return selectedProviders.stream()
                    .map(item -> toV2RuntimeProviderOverride(item, AiCapability.CHAT, explicitRouteKey))
                    .toList();
        }
        StModelRoutingService.ResolvedRoute route = modelRoutingService.resolveForScene(StModelRoutingService.DEFAULT_SCENE);
        List<StModelRoutingService.ResolvedProvider> legacyProviders = route == null || route.providers() == null
                ? List.of()
                : route.providers();
        List<RuntimeProviderOverride> legacyChain = legacyProviders.stream()
                .map(item -> new RuntimeProviderOverride(
                        item.providerKey(),
                        item.displayName(),
                        route == null ? StModelRoutingService.DEFAULT_SCENE : route.sceneKey(),
                        item.stSource(),
                        item.modelName(),
                        item.reverseProxy(),
                        item.proxyPassword(),
                        item.customUrl(),
                        null,
                        null
                ))
                .toList();
        if (aiRoutingService == null) {
            return legacyChain;
        }
        try {
            List<AiRoutingService.ResolvedProvider> v2Providers = aiRoutingService.resolve(AiCapability.CHAT);
            aiRoutingService.shadowCompareChat(legacyProviders, v2Providers);
            if (!aiRoutingService.shouldUseChatV2(request == null ? null : request.conversationId())) {
                return legacyChain;
            }
            if (v2Providers.isEmpty()) {
                if (aiRoutingService.isChatFullyRolledOut()) {
                    throw new StUnavailableException(new IllegalStateException("CHAT V2 已全量启用，但没有可用路由节点"));
                }
                return legacyChain;
            }
            List<RuntimeProviderOverride> result = new java.util.ArrayList<>();
            for (AiRoutingService.ResolvedProvider item : v2Providers) {
                result.add(toV2RuntimeProviderOverride(item, AiCapability.CHAT, AiCapability.CHAT.defaultRouteKey()));
            }
            // During canary rollout the proven legacy chain remains the last pre-token recovery path.
            if (!aiRoutingService.isChatFullyRolledOut()) {
                result.addAll(legacyChain);
            }
            return List.copyOf(result);
        } catch (RuntimeException ex) {
            if (aiRoutingService.isChatFullyRolledOut()) {
                throw ex;
            }
            log.warn("ai routing v2 resolution failed; using legacy chat route reason={}", rootCauseMessage(ex));
            return legacyChain;
        }
    }

    private static RuntimeProviderOverride toV2RuntimeProviderOverride(
            AiRoutingService.ResolvedProvider provider,
            AiCapability capability,
            String routeKey
    ) {
        String vendor = firstNonBlank(provider.vendor()).toLowerCase(java.util.Locale.ROOT);
        boolean custom = "custom".equals(vendor);
        return new RuntimeProviderOverride(
                provider.providerKey(),
                provider.displayName(),
                firstNonBlank(routeKey, capability.defaultRouteKey()),
                custom ? "custom" : vendor,
                provider.modelName(),
                custom ? "" : provider.baseUrl(),
                provider.apiKey(),
                custom ? provider.baseUrl() : "",
                provider.deploymentId(),
                provider.requestTimeoutSeconds()
        );
    }

    private void recordProviderSuccess(RuntimeProviderOverride provider) {
        if (provider == null || "user_byok".equalsIgnoreCase(provider.providerKey())) {
            return;
        }
        try {
            if (provider.aiDeploymentId() != null && aiRoutingService != null) {
                aiRoutingService.recordSuccess(provider.aiDeploymentId());
            } else {
                modelRoutingService.recordSuccess(provider.providerKey());
            }
        } catch (RuntimeException ex) {
            log.warn("provider success telemetry failed providerKey={} reason={}", provider.providerKey(), rootCauseMessage(ex));
        }
    }

    static boolean isAuxiliarySidecarRequest(ChatGenerateRequest request) {
        return request != null && "semantic_annotation".equalsIgnoreCase(firstNonBlank(request.mode()));
    }

    private void recordProviderFailure(RuntimeProviderOverride provider, String message) {
        if (provider == null || "user_byok".equalsIgnoreCase(provider.providerKey())) {
            return;
        }
        try {
            if (provider.aiDeploymentId() != null && aiRoutingService != null) {
                aiRoutingService.recordFailure(provider.aiDeploymentId(), message);
            } else {
                modelRoutingService.recordFailure(provider.providerKey(), message);
            }
        } catch (RuntimeException ex) {
            log.warn("provider failure telemetry failed providerKey={} reason={}", provider.providerKey(), rootCauseMessage(ex));
        }
    }

    private void recordProviderConfigurationError(RuntimeProviderOverride provider, String message) {
        if (provider == null || provider.aiDeploymentId() == null || aiRoutingService == null) {
            return;
        }
        try {
            aiRoutingService.recordConfigurationError(provider.aiDeploymentId(), message);
        } catch (RuntimeException ex) {
            log.warn("provider configuration telemetry failed providerKey={} reason={}",
                    provider.providerKey(), rootCauseMessage(ex));
        }
    }

    private RuntimeProviderOverride toUserRuntimeProviderOverride(UserModelOverride override, boolean preferVisionModel) {
        if (override == null) {
            return null;
        }
        String source = firstNonBlank(override.providerSource()).toLowerCase(java.util.Locale.ROOT);
        String model = preferVisionModel
                ? firstNonBlank(override.visionModelOrFallback(), override.textModelOrFallback())
                : firstNonBlank(override.textModelOrFallback());
        String apiKey = firstNonBlank(override.apiKey());
        if (source.isBlank() || model.isBlank() || apiKey.isBlank()) {
            return null;
        }
        String customUrl = firstNonBlank(override.customUrl());
        if ("custom".equals(source) && customUrl.isBlank()) {
            return null;
        }
        String reverseProxy = runtimeReverseProxyForUserSource(source, customUrl);
        return new RuntimeProviderOverride(
                "user_byok",
                "user_byok",
                preferVisionModel ? "user_byok_vision" : "user_byok",
                source,
                model,
                reverseProxy,
                apiKey,
                customUrl,
                null,
                null
        );
    }

    private static String runtimeReverseProxyForUserSource(String source, String customUrl) {
        String normalized = firstNonBlank(source).toLowerCase(java.util.Locale.ROOT);
        if ("custom".equals(normalized)) {
            return "";
        }
        return switch (normalized) {
            case "siliconflow" -> "https://api.siliconflow.cn/v1";
            case "deepseek" -> "https://api.deepseek.com";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "openai" -> "https://api.openai.com/v1";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "mistralai" -> "https://api.mistral.ai/v1";
            case "moonshot" -> "https://api.moonshot.cn/v1";
            case "xai" -> "https://api.x.ai/v1";
            case "fireworks" -> "https://api.fireworks.ai/inference/v1";
            default -> firstNonBlank(customUrl);
        };
    }

    private AttemptTelemetry startAttemptTelemetry(
            ChatGenerateRequest request,
            RuntimeProviderOverride providerOverride,
            int attemptNo
    ) {
        if (providerOverride == null) {
            return new AttemptTelemetry(
                    request,
                    Math.max(1, attemptNo),
                    "st_default",
                    "st_default",
                    firstNonBlank(properties.getChatCompletionSource()),
                    firstNonBlank(properties.getDefaultModel()),
                    false
            );
        }
        return new AttemptTelemetry(
                request,
                Math.max(1, attemptNo),
                firstNonBlank(providerOverride.providerKey(), "st_default"),
                firstNonBlank(providerOverride.sceneKey(), "st_default"),
                firstNonBlank(providerOverride.chatCompletionSource()),
                firstNonBlank(providerOverride.model()),
                "user_byok".equalsIgnoreCase(providerOverride.providerKey())
        );
    }

    private AttemptTelemetry startDirectAttemptTelemetry(ChatGenerateRequest request, StOaiRuntime oai) {
        RuntimeProviderOverride providerOverride = null;
        try {
            List<RuntimeProviderOverride> chain = resolveRuntimeProviderChain(request);
            providerOverride = chain.isEmpty() ? null : chain.get(0);
        } catch (Throwable ignored) {
            // Observability lookup must not become a prerequisite for generation.
        }
        if (providerOverride != null) {
            return startAttemptTelemetry(request, providerOverride, 1);
        }
        return new AttemptTelemetry(
                request,
                1,
                "st_default",
                "st_default",
                oai == null ? firstNonBlank(properties.getChatCompletionSource()) : firstNonBlank(oai.chatCompletionSource()),
                oai == null ? firstNonBlank(properties.getDefaultModel()) : firstNonBlank(oai.model()),
                false
        );
    }

    private void finishAttemptTelemetry(AttemptTelemetry attempt, boolean cancelled, Exception failure) {
        if (generationTelemetryService == null || attempt == null) {
            return;
        }
        try {
            generationTelemetryService.recordAsync(attempt.finish(cancelled, failure));
        } catch (Throwable telemetryFailure) {
            log.warn(
                    "generation telemetry handoff skipped conversationId={} cause={}",
                    attempt.conversationId(),
                    telemetryFailure.getClass().getSimpleName()
            );
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "" : error.toString();
        }
        return message == null ? "" : message.trim();
    }

    private static String telemetryErrorCode(Throwable error, Integer httpStatus) {
        if (httpStatus != null && httpStatus >= 400) {
            return "HTTP_" + httpStatus;
        }
        Throwable root = error;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = rootCauseMessage(error).toLowerCase(java.util.Locale.ROOT);
        if (message.contains("without assistant content") || message.contains("no assistant content")) {
            return "EMPTY_RESPONSE";
        }
        if (message.contains("upstream error")) {
            return "UPSTREAM_ERROR";
        }
        if (message.contains("timeout") || root instanceof java.net.http.HttpTimeoutException) {
            return "TIMEOUT";
        }
        if (root instanceof java.io.IOException) {
            return "IO_ERROR";
        }
        return "ST_UNAVAILABLE";
    }

    public boolean runtimeChatStop(String avatarUrl, String fileName) {
        try {
            java.util.Map<String, Object> body = java.util.Map.of(
                    "avatar_url", avatarUrl == null ? "" : avatarUrl,
                    "file_name", fileName == null ? "" : fileName
            );
            runtimeChatWriteCapture.capture(0L, "stop", properties.getBaseUrl().resolve(StApiPaths.RUNTIME_CHAT_STOP), body);
            Object resp = postSt(StApiPaths.RUNTIME_CHAT_STOP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Object.class);
            if (resp instanceof java.util.Map<?, ?> m) {
                Object stopped = m.get("stopped");
                if (stopped instanceof Boolean b) {
                    return b;
                }
                if (stopped != null) {
                    return Boolean.parseBoolean(String.valueOf(stopped));
                }
            }
            return false;
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public Object runtimeChatGoldenCaseSave(java.util.Map<String, Object> body) {
        try {
            return postSt(StApiPaths.RUNTIME_CHAT_GOLDEN_CASE_SAVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? java.util.Map.of() : body)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public Object runtimeChatGoldenCaseList() {
        try {
            return restClient.get()
                    .uri(StApiPaths.RUNTIME_CHAT_GOLDEN_CASE_LIST)
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public Object runtimeChatGoldenCaseRun(String caseName) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(StApiPaths.RUNTIME_CHAT_GOLDEN_CASE_RUN)
                            .queryParam("case_name", caseName == null ? "" : caseName)
                            .build())
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private void executeStreamChatCompletions(
            URI url,
            ChatGenerateRequest request,
            Consumer<ChatGenerateChunk> onChunk,
            StStreamControl control,
            StOaiRuntime oai,
            AtomicInteger idx,
            boolean allowReasoningFallback,
            RuntimeProviderOverride providerOverride,
            AttemptTelemetry attempt
    ) throws JsonProcessingException {
        String body = buildStChatCompletionsBody(request, oai, allowReasoningFallback, providerOverride);
        if (generateBodyCapture != null) {
            generateBodyCapture.capture(request.conversationId(), request.mode(), url, body);
        }
        HttpRequest httpRequest = buildChatCompletionsHttpRequest(url, body, providerOverride);

        try {
            CompletableFuture<HttpResponse<java.io.InputStream>> future =
                    stHttpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            control.addOnCancel(() -> future.cancel(true));

            HttpResponse<java.io.InputStream> resp = future.join();
            attempt.setHttpStatus(resp.statusCode());
            if (resp.statusCode() >= 400) {
                long responseBytes = discardResponseBody(resp.body());
                if (shouldRetryWithoutReasoning(resp.statusCode(), oai, allowReasoningFallback)) {
                    executeStreamChatCompletions(
                            url, request, onChunk, control, oai, idx, false, providerOverride, attempt);
                    return;
                }
                throw providerHttpFailure("st generate", resp.statusCode(), responseBytes);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                control.addOnCancel(() -> {
                    try {
                        resp.body().close();
                    } catch (Exception ignored) {
                    }
                });
                String line;
                StringBuilder dataBuf = new StringBuilder();
                boolean emittedContent = false;
                while (!control.isCancelled() && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (dataBuf.length() == 0) continue;
                        String data = dataBuf.toString().trim();
                        dataBuf.setLength(0);
                        if (data.isEmpty()) continue;
                        StreamChunkResult result = emitStreamData(data, request, onChunk, idx);
                        emittedContent = emittedContent || result.emittedContent();
                        if (result.done()) {
                            if (!emittedContent) {
                                throw providerTransientFailure("st generate empty response");
                            }
                            emitDoneChunk(request, onChunk, idx, null);
                            return;
                        }
                        continue;
                    }
                    if (line.startsWith(":")) {
                        // comment/heartbeat
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        String dataLine = line.substring(5).trim();
                        if (!dataLine.isEmpty()) {
                            if (dataBuf.length() > 0) dataBuf.append('\n');
                            dataBuf.append(dataLine);
                        }
                        continue;
                    }
                    String trimmedLine = line.trim();
                    if (trimmedLine.startsWith("{") || trimmedLine.startsWith("[")) {
                        if (dataBuf.length() > 0) dataBuf.append('\n');
                        dataBuf.append(trimmedLine);
                    }
                }
                if (!control.isCancelled() && dataBuf.length() > 0) {
                    StreamChunkResult result = emitStreamData(dataBuf.toString().trim(), request, onChunk, idx);
                    emittedContent = emittedContent || result.emittedContent();
                    if (result.done()) {
                        if (!emittedContent) {
                            throw providerTransientFailure("st generate empty response");
                        }
                        emitDoneChunk(request, onChunk, idx, null);
                        return;
                    }
                }
                if (!control.isCancelled() && !emittedContent) {
                    throw providerTransientFailure("st generate empty response");
                }
            }
        } catch (Exception e) {
            if (control.isCancelled()) {
                // 閸欐牗绉锋稉宥呯秼娴ｆ粓鏁婄拠顖欑瑐閹舵冻绱濋悽鍙樼瑐鐏炲倻濮搁幀浣规簚閺€鑸垫殐娑?STOPPED
                return;
            }
            throw classifyChatAttemptFailure(e, "st generate transport failure");
        }
    }

    private HttpRequest buildChatCompletionsHttpRequest(
            URI url,
            String body,
            RuntimeProviderOverride providerOverride
    ) {
        long timeoutMillis = properties.getReadTimeout().toMillis();
        if (providerOverride != null && providerOverride.requestTimeoutSeconds() != null) {
            timeoutMillis = Duration.ofSeconds(
                    Math.max(5, Math.min(600, providerOverride.requestTimeoutSeconds()))).toMillis();
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(url)
                .timeout(Duration.ofMillis(timeoutMillis))
                .header(HttpHeaders.ACCEPT, "text/event-stream")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        applyCsrfHeader(builder);
        return builder.build();
    }

    private static long discardResponseBody(java.io.InputStream responseBody) throws java.io.IOException {
        if (responseBody == null) {
            return 0L;
        }
        try (java.io.InputStream input = responseBody) {
            return input.transferTo(java.io.OutputStream.nullOutputStream());
        }
    }

    private static String httpFailureMessage(String operation, int statusCode, long responseBytes) {
        return operation + " http " + statusCode + " responseBytes=" + Math.max(0L, responseBytes);
    }

    private static StUnavailableException providerHttpFailure(String operation, int statusCode, long responseBytes) {
        return StUnavailableException.providerFailure(AiProviderCallException.http(
                statusCode,
                httpFailureMessage(operation, statusCode, responseBytes),
                null
        ));
    }

    private static StUnavailableException providerTransientFailure(String message) {
        return StUnavailableException.providerFailure(
                AiProviderCallException.transientFailure(message, null)
        );
    }

    private static StUnavailableException classifyChatAttemptFailure(Exception error, String transportMessage) {
        if (error instanceof StUnavailableException unavailable) {
            return unavailable;
        }
        if (error instanceof IllegalStateException
                && "upstream error".equalsIgnoreCase(firstNonBlank(error.getMessage()))) {
            return StUnavailableException.providerFailure(
                    AiProviderCallException.transientFailure("upstream stream error", error)
            );
        }
        if (hasTransportCause(error)) {
            return StUnavailableException.providerFailure(
                    AiProviderCallException.transientFailure(transportMessage, error)
            );
        }
        return new StUnavailableException(error);
    }

    private static boolean hasTransportCause(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof java.io.IOException && !(cursor instanceof JsonProcessingException)) {
                return true;
            }
            Throwable cause = cursor.getCause();
            if (cause == null || cause == cursor) {
                return false;
            }
            cursor = cause;
        }
        return false;
    }

    private static boolean shouldRetryWithoutReasoning(int statusCode, StOaiRuntime oai, boolean allowReasoningFallback) {
        if (!allowReasoningFallback || statusCode != 400 || oai == null) {
            return false;
        }
        return oai.includeReasoning() || StringUtils.hasText(oai.reasoningEffort());
    }

    /**
     * 闂冭埖顔?5閿涘牐绻嶉拃銉ч獓閸╄櫣顢呴敍澶涚窗閼惧嘲褰?ST chat 韫囶偆鍙庨妴?     * <p>
     * 鐠囥儲鏌熷▔鏇＄箲閸?ST 閻ㄥ嫭鐖ｉ崙?chat 閺佹壆绮嶉敍鍧攅ader + messages閿涘绱濋悽銊ょ艾 continue/regen/swipe 閻ㄥ嫭绉烽幁顖氱暰娴ｅ秳绗岄幁銏狀槻閵?     */
    public Object getChatSnapshot(StChatGetRequest req) {
        try {
            return postSt(StApiPaths.CHATS_GET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", req.avatarUrl(),
                            "file_name", req.fileName()
                    ))
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    /**
     * 闂冭埖顔?5閿涘牐绻嶉拃銉ч獓閸╄櫣顢呴敍澶涚窗娣囨繂鐡?ST chat 韫囶偆鍙庨妴?     */
    public void saveChatSnapshot(StChatSaveRequest req) {
        try {
            postSt(StApiPaths.CHATS_SAVE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", req.avatarUrl(),
                            "file_name", req.fileName(),
                            "chat", req.chat(),
                            "force", req.force() == null ? Boolean.FALSE : req.force()
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    /**
     * 閺傝顢嶆稉鈧敍鍦玹epA閿涘绱伴幎濠勬暏閹?閸斺晜澧滃☉鍫熶紖閸愭瑥鍙?ST chat jsonl閿涘湯T 娴ｆ粈璐熸导姘崇樈鏉╂劘顢戦弮鏈电皑鐎圭偞绨敍澶堚偓?     */
    public boolean deleteChat(String avatarUrl, String chatFileName) {
        String safeAvatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        String safeChatFileName = chatFileName == null ? "" : chatFileName.trim();
        if (safeAvatarUrl.isBlank() || safeChatFileName.isBlank()) {
            return false;
        }
        try {
            postSt(StApiPaths.CHATS_DELETE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", safeAvatarUrl,
                            "chatfile", safeChatFileName
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                return false;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public void runtimeChatAppend(String avatarUrl, String fileName, String userName, String charName, boolean isUser, String messageRef, String mes) {
        runtimeChatAppend(avatarUrl, fileName, userName, charName, isUser, messageRef, mes, false);
    }

    public void runtimeChatAppend(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            boolean isUser,
            String messageRef,
            String mes,
            boolean outputRegexApplied
    ) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("avatar_url", avatarUrl == null ? "" : avatarUrl);
            body.put("file_name", fileName == null ? "" : fileName);
            body.put("user_name", userName == null ? "" : userName);
            body.put("char_name", charName == null ? "" : charName);
            body.put("is_user", isUser);
            body.put("message_ref", messageRef == null ? "" : messageRef);
            body.put("mes", mes == null ? "" : mes);
            body.put("output_regex_applied", outputRegexApplied);
            runtimeChatWriteCapture.capture(0L, "append", properties.getBaseUrl().resolve(StApiPaths.RUNTIME_CHAT_APPEND), body);
            postSt(StApiPaths.RUNTIME_CHAT_APPEND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private String buildRuntimeChatGenerateBody(ChatGenerateRequest request, RuntimeProviderOverride providerOverride) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("avatar_url", request.stAvatarUrl() == null ? "" : request.stAvatarUrl());
        root.put("file_name", request.stChatFileName() == null ? "" : request.stChatFileName());
        root.put("user_name", request.userName() == null ? "" : request.userName());
        root.put("user_persona", request.userPersona() == null ? "" : request.userPersona());
        root.put("char_name", request.charName() == null ? "" : request.charName());
        root.put("stream", request.stream());
        if (StringUtils.hasText(request.mode())) {
            root.put("mode", request.mode());
        }
        if (StringUtils.hasText(request.userMessage())) {
            root.put("user_message", request.userMessage());
        }
        if (StringUtils.hasText(request.stMessageRef())) {
            root.put("message_ref", request.stMessageRef());
        }
        if (request.allowedFeatures() != null && !request.allowedFeatures().isEmpty()) {
            root.putPOJO("allowed_features", request.allowedFeatures());
        }
        root.putPOJO("group_names", request.groupNames() == null ? java.util.List.of() : request.groupNames());
        root.putPOJO("world_names", request.stWorldNames() == null ? java.util.List.of() : request.stWorldNames());
        if (properties.getMemoryLoreMaxTokens() > 0) {
            root.put("memory_lore_max_tokens", properties.getMemoryLoreMaxTokens());
        }
        if (StringUtils.hasText(request.tailSystemPrompt())) {
            root.put("tail_system_prompt", request.tailSystemPrompt().trim());
        }
        if (StringUtils.hasText(request.studioLoreBeforeCharacter())) {
            root.put("studio_lore_before_character", request.studioLoreBeforeCharacter().trim());
        }
        if (StringUtils.hasText(request.studioLoreAfterCharacter())) {
            root.put("studio_lore_after_character", request.studioLoreAfterCharacter().trim());
        }
        if (StringUtils.hasText(request.studioLoreBeforeHistory())) {
            root.put("studio_lore_before_history", request.studioLoreBeforeHistory().trim());
        }
        if (StringUtils.hasText(request.runtimePresetBundle())) {
            root.set("runtime_preset_bundle", objectMapper.readTree(request.runtimePresetBundle()));
        }
        if (providerOverride != null) {
            root.put("chat_completion_source", providerOverride.chatCompletionSource());
            root.put("model", providerOverride.model());
            root.put("reverse_proxy", providerOverride.reverseProxy());
            root.put("proxy_password", providerOverride.proxyPassword());
            if (StringUtils.hasText(providerOverride.customUrl())) {
                root.put("custom_url", providerOverride.customUrl());
            }
            if (StringUtils.hasText(providerOverride.providerKey())) {
                root.put("provider_key", providerOverride.providerKey());
            }
            if (StringUtils.hasText(providerOverride.sceneKey())) {
                root.put("route_scene", providerOverride.sceneKey());
            }
            if (providerOverride.requestTimeoutSeconds() != null && providerOverride.requestTimeoutSeconds() > 0) {
                root.put("request_timeout_seconds", Math.max(5, Math.min(600, providerOverride.requestTimeoutSeconds())));
            }
        }
        return objectMapper.writeValueAsString(root);
    }

    /**
     * 閺傝顢嶆稉鈧敍鍦玹epA閿涘绱伴崚鐘绘珟 ST chat 娑擃厽娓堕崥搴濈閺?assistant 濞戝牊浼呴敍鍫㈡暏娴?regenerate 閺堚偓閸氬簼绔撮弶鈥虫礀婢跺稄绱氶妴?     */
    public void runtimeChatPopLastAssistant(String avatarUrl, String fileName, String userName, String charName) {
        try {
            postSt(StApiPaths.RUNTIME_CHAT_POP_LAST_ASSISTANT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", avatarUrl == null ? "" : avatarUrl,
                            "file_name", fileName == null ? "" : fileName,
                            "user_name", userName == null ? "" : userName,
                            "char_name", charName == null ? "" : charName
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public void runtimeChatReplaceLastAssistant(String avatarUrl, String fileName, String userName, String charName, String messageRef, String mes) {
        runtimeChatReplaceLastAssistant(avatarUrl, fileName, userName, charName, messageRef, mes, false);
    }

    public void runtimeChatReplaceLastAssistant(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            String messageRef,
            String mes,
            boolean outputRegexApplied
    ) {
        try {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("avatar_url", avatarUrl == null ? "" : avatarUrl);
            body.put("file_name", fileName == null ? "" : fileName);
            body.put("user_name", userName == null ? "" : userName);
            body.put("char_name", charName == null ? "" : charName);
            body.put("message_ref", messageRef == null ? "" : messageRef);
            body.put("mes", mes == null ? "" : mes);
            body.put("output_regex_applied", outputRegexApplied);
            runtimeChatWriteCapture.capture(0L, "replace-last-assistant", properties.getBaseUrl().resolve(StApiPaths.RUNTIME_CHAT_REPLACE_LAST_ASSISTANT), body);
            postSt(StApiPaths.RUNTIME_CHAT_REPLACE_LAST_ASSISTANT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public Object runtimeChatTail(String avatarUrl, String fileName, String userName, String charName, int limit) {
        try {
            return postSt(StApiPaths.RUNTIME_CHAT_TAIL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", avatarUrl == null ? "" : avatarUrl,
                            "file_name", fileName == null ? "" : fileName,
                            "user_name", userName == null ? "" : userName,
                            "char_name", charName == null ? "" : charName,
                            "limit", Math.max(1, Math.min(200, limit))
                    ))
                    .retrieve()
                    .body(Object.class);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    /**
     * 閺傝顢嶆稉鈧敍鍦玹epA閿涘绱伴悽?ST 閹稿鍙鹃張顒€婀?chat + 鐟欐帟澹婇崡鈩冪€柅?messages閿涘湯pring 娑撳秴鍟€閹疯壈顥?prompt閿涘鈧?     */
    @SuppressWarnings("unchecked")
    public List<java.util.Map<String, String>> runtimeChatBuildMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> groupNames,
            List<String> worldNames
    ) {
        return runtimeChatBuildMessages(avatarUrl, fileName, userName, charName, groupNames, worldNames, null, null);
    }

    @SuppressWarnings("unchecked")
    public List<java.util.Map<String, String>> runtimeChatBuildMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> groupNames,
            List<String> worldNames,
            String runtimePresetBundle
    ) {
        return runtimeChatBuildMessages(
                avatarUrl, fileName, userName, charName, groupNames, worldNames, runtimePresetBundle, null);
    }

    @SuppressWarnings("unchecked")
    public List<java.util.Map<String, String>> runtimeChatBuildMessages(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            List<String> groupNames,
            List<String> worldNames,
            String runtimePresetBundle,
            String userPersona
    ) {
        try {
            java.util.Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
            requestBody.put("avatar_url", avatarUrl == null ? "" : avatarUrl);
            requestBody.put("file_name", fileName == null ? "" : fileName);
            requestBody.put("user_name", userName == null ? "" : userName);
            requestBody.put("user_persona", userPersona == null ? "" : userPersona);
            requestBody.put("char_name", charName == null ? "" : charName);
            requestBody.put("group_names", groupNames == null ? java.util.List.of() : groupNames);
            requestBody.put("world_names", worldNames == null ? java.util.List.of() : worldNames);
            if (properties.getMemoryLoreMaxTokens() > 0) {
                requestBody.put("memory_lore_max_tokens", properties.getMemoryLoreMaxTokens());
            }
            if (StringUtils.hasText(runtimePresetBundle)) {
                requestBody.put("runtime_preset_bundle", objectMapper.readValue(runtimePresetBundle, Object.class));
            }
            Object body = postSt(StApiPaths.RUNTIME_CHAT_BUILD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Object.class);
            if (body instanceof java.util.Map<?, ?> m) {
                Object raw = m.get("messages");
                if (raw instanceof List<?> l) {
                    return (List<java.util.Map<String, String>>) l;
                }
            }
            return java.util.List.of();
        } catch (JsonProcessingException e) {
            throw new StUnavailableException(e);
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public List<StCharacterSummary> listCharactersAll() {
        try {
            Object body = postSt(StApiPaths.CHARACTERS_ALL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of())
                    .retrieve()
                    .body(Object.class);
            List<?> list = unwrapCharacterListBody(body);
            if (list == null) {
                return List.of();
            }
            return list.stream()
                    .map(this::mapCharacterSummary)
                    .filter(x -> x != null && x.name() != null && !x.name().isBlank())
                    .toList();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public List<StWorldbookOptionDto> listWorldbooks() {
        try {
            Object body = postSt(StApiPaths.WORLDINFO_LIST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of())
                    .retrieve()
                    .body(Object.class);
            if (!(body instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .map(this::mapWorldbookOption)
                    .filter(x -> x != null && x.fileId() != null && !x.fileId().isBlank())
                    .toList();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public String runtimeChatApplyOutputRegex(
            String avatarUrl,
            String fileName,
            String userName,
            String charName,
            String mes
    ) {
        String raw = mes == null ? "" : mes.trim();
        try {
            java.util.Map<String, Object> body = java.util.Map.of(
                    "avatar_url", avatarUrl == null ? "" : avatarUrl,
                    "file_name", fileName == null ? "" : fileName,
                    "user_name", userName == null ? "" : userName,
                    "char_name", charName == null ? "" : charName,
                    "mes", raw
            );
            String responseBody = postSt(StApiPaths.RUNTIME_CHAT_APPLY_OUTPUT_REGEX)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = responseBody == null || responseBody.isBlank()
                    ? null
                    : objectMapper.readTree(responseBody);
            JsonNode mesNode = response == null ? null : response.get("mes");
            if (mesNode == null || !mesNode.isTextual()) {
                throw new StUnavailableException(
                        new IllegalStateException("runtime output-regex response missing textual mes")
                );
            }
            return mesNode.textValue();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException | JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
    }

    public String readWorldbookRaw(String name) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isBlank()) {
            return "";
        }
        try {
            String raw = postSt(StApiPaths.WORLDINFO_GET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("name", safeName))
                    .retrieve()
                    .body(String.class);
            return raw == null ? "" : raw;
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public JsonNode readStSettingsEnvelope() {
        String raw;
        try {
            raw = postSt(StApiPaths.SETTINGS_GET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new StUnavailableException(e);
        }
    }

    public void saveWorldbook(String name, java.util.Map<String, Object> data) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isBlank() || data == null) {
            throw new StUnavailableException(new IllegalArgumentException("worldbook name/data required"));
        }
        try {
            postSt(StApiPaths.WORLDINFO_EDIT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("name", safeName, "data", data))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public boolean deleteWorldbook(String name) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isBlank()) {
            return false;
        }
        try {
            postSt(StApiPaths.WORLDINFO_DELETE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("name", safeName))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                return false;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private List<?> unwrapCharacterListBody(Object body) {
        if (body instanceof List<?> l) {
            return l;
        }
        if (body instanceof java.util.Map<?, ?> m) {
            for (String key : java.util.List.of("data", "characters", "results")) {
                Object raw = m.get(key);
                if (raw instanceof List<?> l2) {
                    return l2;
                }
            }
        }
        return null;
    }

    private StWorldbookOptionDto mapWorldbookOption(Object value) {
        if (!(value instanceof java.util.Map<?, ?> m)) {
            return null;
        }
        String fileId = safeStr(m.get("file_id"));
        String name = firstNonBlank(safeStr(m.get("name")), fileId);
        if (fileId.isBlank()) {
            return null;
        }
        return new StWorldbookOptionDto(fileId, name);
    }

    public StCharacterDetail getCharacter(StCharacterGetRequest req) {
        String avatarUrl = req == null ? "" : req.avatarUrl();
        if (avatarUrl != null && !avatarUrl.isBlank() && !avatarUrl.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            avatarUrl = avatarUrl.trim() + ".png";
        }
        try {
            Object body = postSt(StApiPaths.CHARACTERS_GET)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("avatar_url", avatarUrl == null ? "" : avatarUrl))
                    .retrieve()
                    .body(Object.class);
            return mapCharacterDetail(body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public String syncCharacterCard(StCharacterDetail detail, String preferredAvatarUrl) {
        String avatarUrl = normalizeAvatarUrl(preferredAvatarUrl);
        if (!StringUtils.hasText(avatarUrl)) {
            throw new IllegalArgumentException("preferredAvatarUrl required");
        }
        try {
            StCharacterDetail existing = getCharacter(new StCharacterGetRequest(avatarUrl));
            java.util.Map<String, Object> body = buildCharacterEditorBody(detail, avatarUrl, existing);
            if (existing == null) {
                String created = postSt(StApiPaths.CHARACTERS_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
                return normalizeAvatarUrl(created == null ? avatarUrl : created);
            }
            postSt(StApiPaths.CHARACTERS_EDIT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return avatarUrl;
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private java.util.Map<String, Object> buildCharacterEditorBody(
            StCharacterDetail detail,
            String avatarUrl,
            StCharacterDetail existing
    ) {
        String name = firstNonBlank(detail == null ? "" : detail.name(), stripPngExtension(avatarUrl), "Character");
        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        String jsonData = editorJsonData(detail, existing);
        if (StringUtils.hasText(jsonData)) {
            body.put("json_data", jsonData);
        }
        body.put("avatar_url", avatarUrl);
        body.put("file_name", stripPngExtension(avatarUrl));
        body.put("ch_name", name);
        body.put("description", firstNonBlank(detail == null ? "" : detail.description()));
        body.put("personality", firstNonBlank(detail == null ? "" : detail.personality()));
        body.put("scenario", firstNonBlank(detail == null ? "" : detail.scenario()));
        body.put("first_mes", firstNonBlank(detail == null ? "" : detail.firstMes()));
        body.put("mes_example", firstNonBlank(detail == null ? "" : detail.mesExample()));
        body.put("creator_notes", firstNonBlank(detail == null ? "" : detail.creatorNotes()));
        body.put("system_prompt", firstNonBlank(detail == null ? "" : detail.systemPrompt()));
        body.put("post_history_instructions", firstNonBlank(detail == null ? "" : detail.postHistoryInstructions()));
        body.put("alternate_greetings", detail == null || detail.alternateGreetings() == null
                ? java.util.List.of()
                : detail.alternateGreetings());
        body.put("tags", detail == null || detail.tags() == null ? java.util.List.of() : detail.tags());
        body.put("creator", firstNonBlank(detail == null ? "" : detail.creator()));
        body.put("talkativeness", 0.5);
        body.put("fav", "false");
        body.put("chat", name + " - app");
        body.put("create_date", java.time.Instant.now().toString());
        return body;
    }

    private String editorJsonData(StCharacterDetail detail, StCharacterDetail existing) {
        String raw = firstNonBlank(
                detail == null ? "" : detail.rawJson(),
                existing == null ? "" : existing.rawJson()
        );
        if (looksLikeCharacterJson(raw)) {
            return raw;
        }
        String embedded = firstNonBlank(
                detail == null ? "" : detail.embeddedCharacterBookJson(),
                existing == null ? "" : existing.embeddedCharacterBookJson()
        );
        if (!StringUtils.hasText(embedded)) {
            return "";
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = objectMapper.createObjectNode();
            data.set("character_book", objectMapper.readTree(embedded));
            root.set("data", data);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean looksLikeCharacterJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            return root != null
                    && root.isObject()
                    && (root.has("data")
                    || root.has("spec")
                    || root.has("first_mes")
                    || root.has("personality")
                    || root.has("scenario"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeAvatarUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return "";
        }
        if (!value.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            value = value + ".png";
        }
        return value;
    }

    private static String stripPngExtension(String avatarUrl) {
        String value = avatarUrl == null ? "" : avatarUrl.trim();
        if (value.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    public Object importCharacterPng(byte[] bytes, String originalFilename, StCharacterImportRequest req) {
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file_type", req.fileType() == null ? "png" : req.fileType());
            if (req.preservedName() != null && !req.preservedName().isBlank()) {
                form.add("preserved_name", req.preservedName());
            }
            ByteArrayResource filePart = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return originalFilename == null || originalFilename.isBlank() ? "character.png" : originalFilename;
                }
            };
            form.add("avatar", filePart);

            Object imported = postSt(StApiPaths.CHARACTERS_IMPORT)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Object.class);
            if (imported instanceof java.util.Map<?, ?> map) {
                java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                String fileName = safeStr(copy.get("file_name"));
                if (!fileName.isBlank() && !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                    copy.put("file_name", fileName + ".png");
                }
                return copy;
            }
            return imported;
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public byte[] exportCharacterPng(String avatarUrl) {
        String safeAvatarUrl = normalizeAvatarUrl(avatarUrl);
        if (!StringUtils.hasText(safeAvatarUrl)) {
            throw new IllegalArgumentException("avatarUrl required");
        }
        try {
            byte[] exported = postSt(StApiPaths.CHARACTERS_EXPORT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "format", "png",
                            "avatar_url", safeAvatarUrl
                    ))
                    .retrieve()
                    .body(byte[].class);
            if (exported == null || exported.length == 0) {
                throw new StUnavailableException(new IllegalStateException("ST returned an empty character PNG export"));
            }
            return exported;
        } catch (RestClientResponseException e) {
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    public boolean deleteCharacter(String avatarUrl, boolean deleteChats) {
        String safeAvatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        if (safeAvatarUrl.isBlank()) {
            return false;
        }
        try {
            postSt(StApiPaths.CHARACTERS_DELETE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of(
                            "avatar_url", safeAvatarUrl,
                            "delete_chats", deleteChats
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) {
                return false;
            }
            throw new StUnavailableException(e);
        } catch (RestClientException e) {
            throw new StUnavailableException(e);
        }
    }

    private StCharacterSummary mapCharacterSummary(Object o) {
        if (!(o instanceof java.util.Map<?, ?> m)) return null;
        String name = safeStr(m.get("name"));
        String avatar = firstNonBlank(
                safeStr(m.get("avatar")),
                safeStr(m.get("avatar_url")),
                safeStr(m.get("avatarUrl")),
                safeStr(m.get("file_name")),
                safeStr(m.get("fileName")));
        if (avatar.isBlank() && !name.isBlank()) {
            // 婢舵碍鏆?ST 閸椻剝鏋冩禒璺烘倳娑撳氦顫楅懝鎻掓倱閸?.png
            String base = name.trim();
            if (!base.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                avatar = base + ".png";
            } else {
                avatar = base;
            }
        }
        String description = safeStr(m.get("description"));
        if (description.isBlank()) {
            description = safeStr(m.get("desc"));
        }
        return new StCharacterSummary(name, avatar, description, longValue(m.get("date_added")));
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) return "";
        for (String p : parts) {
            if (p != null && !p.isBlank()) return p.trim();
        }
        return "";
    }

    private StCharacterDetail mapCharacterDetail(Object o) {
        if (!(o instanceof java.util.Map<?, ?> m)) return null;
        Map<?, ?> data = asMap(m.get("data"));
        Map<?, ?> extensions = asMap(data.get("extensions"));
        String name = firstNonBlank(safeStr(data.get("name")), safeStr(m.get("name")));
        String avatar = firstNonBlank(
                safeStr(m.get("avatar")),
                safeStr(m.get("avatar_url")),
                safeStr(m.get("avatarUrl")));
        Object error = m.get("error");
        if ((error instanceof Boolean b && b) || (data.isEmpty() && name.isBlank() && avatar.isBlank())) {
            return null;
        }
        String description = firstNonBlank(
                safeStr(data.get("description")),
                safeStr(m.get("description")),
                safeStr(m.get("desc")));
        String scenario = firstNonBlank(safeStr(data.get("scenario")), safeStr(m.get("scenario")));
        String firstMes = firstNonBlank(
                safeStr(data.get("first_mes")),
                safeStr(m.get("first_mes")),
                safeStr(m.get("firstMes")));
        String personality = firstNonBlank(safeStr(data.get("personality")), safeStr(m.get("personality")));
        List<String> tags = firstNonEmptyList(
                toStringList(data.get("tags")),
                toStringList(m.get("tags")));
        List<String> alternateGreetings = firstNonEmptyList(
                toStringList(data.get("alternate_greetings")),
                toStringList(m.get("alternate_greetings")),
                toStringList(m.get("alternateGreetings")));
        String mesExample = firstNonBlank(
                safeStr(data.get("mes_example")),
                safeStr(m.get("mes_example")),
                safeStr(m.get("mesExample")));
        String systemPrompt = firstNonBlank(
                safeStr(data.get("system_prompt")),
                safeStr(m.get("system_prompt")),
                safeStr(m.get("systemPrompt")));
        String postHistoryInstructions =
                firstNonBlank(
                        safeStr(data.get("post_history_instructions")),
                        safeStr(m.get("post_history_instructions")),
                        safeStr(m.get("postHistoryInstructions")));
        String creatorNotes = firstNonBlank(
                safeStr(data.get("creator_notes")),
                safeStr(m.get("creator_notes")),
                safeStr(m.get("creatorNotes")));
        String creator = firstNonBlank(safeStr(data.get("creator")), safeStr(m.get("creator")));
        List<String> worldNames = normalizeWorldNames(firstNonBlank(
                safeStr(extensions.get("world")),
                safeStr(m.get("world")),
                safeStr(m.get("world_name")),
                safeStr(m.get("worldName"))));
        String embeddedCharacterBookJson = writeJsonQuietly(data.get("character_book"));
        String rawJson = writeJsonQuietly(m);
        return new StCharacterDetail(
                name,
                avatar,
                description,
                scenario,
                firstMes,
                personality,
                tags,
                alternateGreetings,
                mesExample,
                systemPrompt,
                postHistoryInstructions,
                creatorNotes,
                creator,
                worldNames,
                embeddedCharacterBookJson,
                rawJson);
    }

    private static Map<?, ?> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private List<String> normalizeWorldNames(Object raw) {
        if (raw == null) {
            return java.util.List.of();
        }
        if (raw instanceof List<?> list) {
            return toStringList(list);
        }
        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return java.util.List.of();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                JsonNode node = objectMapper.readTree(value);
                if (node.isArray()) {
                    java.util.List<String> result = new java.util.ArrayList<>();
                    node.forEach(item -> {
                        String text = item == null ? "" : item.asText("").trim();
                        if (!text.isBlank()) {
                            result.add(text);
                        }
                    });
                    return result.stream().distinct().toList();
                }
            } catch (JsonProcessingException ignored) {
                // fall back to single-name handling below
            }
        }
        return java.util.List.of(value);
    }

    private String writeJsonQuietly(Object raw) {
        if (raw == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (JsonProcessingException ignored) {
            return "";
        }
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return java.util.List.of();
        }
        return list.stream()
                .map(x -> x == null ? "" : String.valueOf(x).trim())
                .filter(s -> !s.isBlank())
                .toList();
    }

    @SafeVarargs
    private static List<String> firstNonEmptyList(List<String>... candidates) {
        if (candidates == null) {
            return java.util.List.of();
        }
        for (List<String> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return java.util.List.of();
    }

    private static String safeStr(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Long longValue(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            String value = String.valueOf(raw).trim();
            return value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildStChatCompletionsBody(
            ChatGenerateRequest request,
            StOaiRuntime oai,
            boolean includeReasoning,
            RuntimeProviderOverride providerOverride
    ) throws JsonProcessingException {
        OpenRouterGenerationSettingsService.ResolvedSettings adminSettings = generationSettingsService.resolveForRuntime();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "normal");
        root.put("stream", true);
        boolean replySuggestionsMode = request != null && "reply_suggestions".equalsIgnoreCase(firstNonBlank(request.mode()));
        if (StringUtils.hasText(request.mode())) {
            root.put("mode", request.mode());
        }
        if (request.allowedFeatures() != null && !request.allowedFeatures().isEmpty()) {
            root.putPOJO("allowed_features", request.allowedFeatures());
        }
        String chatSource = providerOverride == null ? oai.chatCompletionSource() : providerOverride.chatCompletionSource();
        root.put("chat_completion_source", chatSource);
        String model = providerOverride == null
                ? pickRuntimeModel(oai, adminSettings.defaultModel())
                : providerOverride.model();
        root.put("model", model);
        root.put("reverse_proxy", providerOverride == null ? oai.reverseProxy() : providerOverride.reverseProxy());
        root.put("proxy_password", providerOverride == null ? oai.proxyPassword() : providerOverride.proxyPassword());
        if (includeReasoning && oai.includeReasoning()) {
            root.put("include_reasoning", true);
        }
        if (includeReasoning && StringUtils.hasText(oai.reasoningEffort())) {
            root.put("reasoning_effort", oai.reasoningEffort());
        }
        if (properties.isPreferStPresetParams() && oai != null) {
            if (oai.tempOpenai() != null) {
                root.put("temperature", oai.tempOpenai());
            }
            if (oai.openaiMaxTokens() != null && oai.openaiMaxTokens() > 0) {
                root.put("max_tokens", oai.openaiMaxTokens());
            }
            if (oai.topPOpenai() != null && oai.topPOpenai() > 0d) {
                root.put("top_p", oai.topPOpenai());
            }
            if (oai.freqPenOpenai() != null) {
                root.put("frequency_penalty", oai.freqPenOpenai());
            }
            if (oai.presPenOpenai() != null) {
                root.put("presence_penalty", oai.presPenOpenai());
            }
        } else {
            root.put("temperature", adminSettings.defaultTemperature());
            if (adminSettings.defaultMaxOutputTokens() > 0) {
                root.put("max_tokens", adminSettings.defaultMaxOutputTokens());
            }
            if (adminSettings.topP() > 0d) {
                root.put("top_p", adminSettings.topP());
            }
            if (adminSettings.frequencyPenalty() > -999d) {
                root.put("frequency_penalty", adminSettings.frequencyPenalty());
            }
            if (adminSettings.presencePenalty() > -999d) {
                root.put("presence_penalty", adminSettings.presencePenalty());
            }
            if (adminSettings.stopSequences() != null && !adminSettings.stopSequences().isEmpty()) {
                root.putPOJO("stop", adminSettings.stopSequences());
            }
        }
        String customUrl = providerOverride == null ? oai.customUrl() : providerOverride.customUrl();
        if ("custom".equals(chatSource) && StringUtils.hasText(customUrl)) {
            root.put("custom_url", customUrl);
        }
        root.set("messages", objectMapper.readTree(buildMessagesJson(request)));

        // Align with ST web defaults so the upstream request shape is closer (Golden Diff).
        // These fields are safe defaults and can be made configurable later if needed.
        root.put("enable_web_search", false);
        root.put("request_images", false);
        root.put("request_image_resolution", "");
        root.put("request_image_aspect_ratio", "");
        root.put("custom_prompt_post_processing", "merge_tools");
        if (properties.isPreferStPresetParams() && oai != null) {
            if (StringUtils.hasText(oai.verbosity())) {
                root.put("verbosity", oai.verbosity());
            }
            Integer topK = normalizeTopKForOpenAiCompatible(oai.topKOpenai());
            if (topK != null) {
                root.put("top_k", topK);
            }
            if (oai.minPOpenai() != null) {
                root.put("min_p", oai.minPOpenai());
            }
            if (oai.topAOpenai() != null) {
                root.put("top_a", oai.topAOpenai());
            }
            if (oai.repetitionPenaltyOpenai() != null) {
                root.put("repetition_penalty", oai.repetitionPenaltyOpenai());
            }
        } else {
            root.put("verbosity", "low");
            root.put("top_k", 2);
            root.put("min_p", 0);
            root.put("top_a", 0);
            root.put("repetition_penalty", 1);
        }
        root.put("use_fallback", false);
        root.putPOJO("provider", java.util.List.of());
        root.putPOJO("quantizations", java.util.List.of());
        if (properties.isPreferStPresetParams() && oai != null) {
            if (oai.openrouterAllowFallbacks() != null) {
                root.put("allow_fallbacks", oai.openrouterAllowFallbacks());
            }
            if (StringUtils.hasText(oai.openrouterMiddleout())) {
                root.put("middleout", oai.openrouterMiddleout());
            }
        } else {
            root.put("allow_fallbacks", false);
            root.put("middleout", "on");
        }

        if (replySuggestionsMode) {
            applyReplySuggestionPresetSamplingOverrides(root, request.runtimePresetBundle());
        }

        if (StringUtils.hasText(request.userName())) {
            root.put("user_name", request.userName());
        }
        if (StringUtils.hasText(request.charName())) {
            root.put("char_name", request.charName());
        }
        if (request.groupNames() != null && !request.groupNames().isEmpty()) {
            root.putPOJO("group_names", request.groupNames());
        } else {
            root.putPOJO("group_names", java.util.List.of());
        }

        return objectMapper.writeValueAsString(root);
    }

    private static String pickRuntimeModel(StOaiRuntime oai, String adminModel) {
        String runtimeModel = oai == null ? "" : firstNonBlank(oai.model());
        String overrideModel = firstNonBlank(adminModel);
        String source = oai == null ? "" : firstNonBlank(oai.chatCompletionSource()).toLowerCase(java.util.Locale.ROOT);

        if (isValidModelOverrideForSource(source, overrideModel)) {
            return overrideModel;
        }
        if (StringUtils.hasText(runtimeModel)) {
            return runtimeModel;
        }
        if (StringUtils.hasText(overrideModel)) {
            return overrideModel;
        }
        return "";
    }

    private static boolean isValidModelOverrideForSource(String source, String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }
        String normalizedSource = source == null ? "" : source.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedModel = model.trim();
        String lowerModel = normalizedModel.toLowerCase(java.util.Locale.ROOT);
        if ("deepseek".equals(normalizedSource)) {
            return "deepseek-chat".equals(lowerModel) || "deepseek-reasoner".equals(lowerModel);
        }
        if ("openrouter".equals(normalizedSource)) {
            // OpenRouter runtime expects provider/model style ids such as deepseek/deepseek-chat.
            return model.contains("/") && !model.contains(":");
        }
        if ("openai".equals(normalizedSource) || "xai".equals(normalizedSource) || "groq".equals(normalizedSource)) {
            return !normalizedModel.contains("/") && !normalizedModel.contains(":");
        }
        return true;
    }

    private static Integer normalizeTopKForOpenAiCompatible(Integer value) {
        if (value == null) {
            return null;
        }
        if (value == 0) {
            return -1;
        }
        if (value < -1) {
            return -1;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    private void applyReplySuggestionPresetSamplingOverrides(ObjectNode root, String runtimePresetBundle) {
        if (root == null || !StringUtils.hasText(runtimePresetBundle)) {
            return;
        }
        try {
            JsonNode bundle = objectMapper.readTree(runtimePresetBundle);
            JsonNode generation = bundle == null ? null : bundle.path("generation");
            if (generation == null || !generation.isObject()) {
                return;
            }
            putPresetDouble(root, "temperature", generation, "temperature", "temp_openai");
            putPresetInteger(root, "max_tokens", generation, "openai_max_tokens", "max_tokens");
            putPresetDouble(root, "top_p", generation, "top_p", "top_p_openai");
            putPresetDouble(root, "frequency_penalty", generation, "frequency_penalty", "freq_pen_openai");
            putPresetDouble(root, "presence_penalty", generation, "presence_penalty", "pres_pen_openai");
            putPresetString(root, "verbosity", generation, "verbosity_openai", "verbosity");
            Integer topK = readPresetInteger(generation, "top_k", "top_k_openai");
            if (topK != null) {
                root.put("top_k", normalizeTopKForOpenAiCompatible(topK));
            }
            putPresetDouble(root, "min_p", generation, "min_p", "min_p_openai");
            putPresetDouble(root, "top_a", generation, "top_a", "top_a_openai");
            putPresetDouble(root, "repetition_penalty", generation, "repetition_penalty", "repetition_penalty_openai");
        } catch (Exception e) {
            log.warn("reply suggestion preset sampling override skipped: {}", rootCauseMessage(e));
        }
    }

    private static void putPresetDouble(ObjectNode root, String targetField, JsonNode source, String... sourceFields) {
        Double value = readPresetDouble(source, sourceFields);
        if (value != null) {
            root.put(targetField, value);
        }
    }

    private static void putPresetInteger(ObjectNode root, String targetField, JsonNode source, String... sourceFields) {
        Integer value = readPresetInteger(source, sourceFields);
        if (value != null && value > 0) {
            root.put(targetField, value);
        }
    }

    private static void putPresetString(ObjectNode root, String targetField, JsonNode source, String... sourceFields) {
        String value = readPresetString(source, sourceFields);
        if (StringUtils.hasText(value)) {
            root.put(targetField, value);
        }
    }

    private static Double readPresetDouble(JsonNode source, String... fields) {
        JsonNode node = readPresetNode(source, fields);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        try {
            String text = node.asText("").trim();
            return text.isBlank() ? null : Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer readPresetInteger(JsonNode source, String... fields) {
        JsonNode node = readPresetNode(source, fields);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isNumber()) {
            return (int) Math.round(node.asDouble());
        }
        try {
            String text = node.asText("").trim();
            return text.isBlank() ? null : Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String readPresetString(JsonNode source, String... fields) {
        JsonNode node = readPresetNode(source, fields);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static JsonNode readPresetNode(JsonNode source, String... fields) {
        if (source == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            if (!StringUtils.hasText(field)) {
                continue;
            }
            JsonNode node = source.get(field);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String buildMessagesJson(ChatGenerateRequest request) throws JsonProcessingException {
        var arr = objectMapper.createArrayNode();

        // Align with ST web: prepend the "Write X's next reply..." system block.
        String charName = request == null ? "" : firstNonBlank(request.charName());
        String userName = request == null ? "" : firstNonBlank(request.userName());
        boolean replySuggestionsMode = request != null && "reply_suggestions".equalsIgnoreCase(firstNonBlank(request.mode()));
        String tailSystemPrompt = request == null ? "" : firstNonBlank(request.tailSystemPrompt());
        boolean hasWriteSystem = false;
        if (!replySuggestionsMode && request != null && request.messages() != null && !request.messages().isEmpty() && StringUtils.hasText(charName)) {
            String who = StringUtils.hasText(userName) ? userName.trim() : "user";
            String expected = "Write " + charName.trim() + "'s next reply in a fictional chat between " + charName.trim() + " and " + who + ".";
            for (ChatMessage m : request.messages()) {
                if (m == null) continue;
                String role = safeRole(m.role());
                String content = firstNonBlank(m.content());
                if ("system".equalsIgnoreCase(role) && expected.equals(content)) {
                    hasWriteSystem = true;
                    break;
                }
                if ("system".equalsIgnoreCase(role) && content.startsWith(expected)) {
                    hasWriteSystem = true;
                    break;
                }
            }
        }

        if (!replySuggestionsMode && StringUtils.hasText(charName) && !hasWriteSystem) {
            String who = StringUtils.hasText(userName) ? userName.trim() : "user";
            arr.add(objectMapper.createObjectNode()
                    .put("role", "system")
                    .put("content", "Write " + charName.trim() + "'s next reply in a fictional chat between " + charName.trim() + " and " + who + "."));
        }

        // Copy messages, but insert "[Start a new Chat]" after the leading system blocks (ST web style).
        if (request != null && request.messages() != null && !request.messages().isEmpty()) {
            boolean hasStartMarker = false;
            for (ChatMessage m : request.messages()) {
                if (m == null) continue;
                String role = safeRole(m.role());
                String content = firstNonBlank(m.content());
                if ("system".equalsIgnoreCase(role) && "[Start a new Chat]".equals(content)) {
                    hasStartMarker = true;
                    break;
                }
            }

            int leadingSystemCount = 0;
            for (ChatMessage m : request.messages()) {
                if (m == null) continue;
                String role = safeRole(m.role());
                if ("system".equalsIgnoreCase(role)) {
                    leadingSystemCount++;
                } else {
                    break;
                }
            }

            int i = 0;
            for (ChatMessage m : request.messages()) {
                if (m == null) continue;
                if (!replySuggestionsMode && !hasStartMarker && i == leadingSystemCount) {
                    arr.add(objectMapper.createObjectNode().put("role", "system").put("content", "[Start a new Chat]"));
                }
                arr.add(toMessageNode(m));
                i++;
            }
        } else {
            // no messages 閳?marker then user turn
            arr.add(objectMapper.createObjectNode().put("role", "system").put("content", "[Start a new Chat]"));
            String content = request == null || request.userMessage() == null ? "" : request.userMessage();
            arr.add(objectMapper.createObjectNode().put("role", "user").put("content", content));
        }

        if (!replySuggestionsMode && StringUtils.hasText(tailSystemPrompt)) {
            arr.add(objectMapper.createObjectNode()
                    .put("role", "system")
                    .put("content", tailSystemPrompt.trim()));
        }

        return objectMapper.writeValueAsString(arr);
    }

    private ObjectNode toMessageNode(ChatMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", safeRole(message == null ? null : message.role()));
        if (message != null && message.hasStructuredContent()) {
            var contentArray = objectMapper.createArrayNode();
            for (var part : message.contentParts()) {
                if (part == null) {
                    continue;
                }
                if (part.isText()) {
                    contentArray.add(objectMapper.createObjectNode()
                            .put("type", "text")
                            .put("text", part.text()));
                    continue;
                }
                if (part.isImageUrl()) {
                    contentArray.add(objectMapper.createObjectNode()
                            .put("type", "image_url")
                            .set("image_url", objectMapper.createObjectNode().put("url", part.url())));
                }
            }
            node.set("content", contentArray);
            return node;
        }
        node.put("content", message == null ? "" : firstNonBlank(message.content()));
        return node;
    }

    private static String safeRole(String role) {
        return StringUtils.hasText(role) ? role : "user";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    /**
     * 鐟欙絾鐎?ST 娴狅絿鎮婃潻鏂挎礀閻?OpenAI 妞嬪孩鐗稿ù浣哥础 chunk閿涘本褰侀崣鏍у讲閹峰吋甯撮弬鍥ㄦ拱閵?     * <p>
     * 閸忕厧顔愯ぐ銏♀偓浣恒仛娓氬绱?
     * {"choices":[{"delta":{"content":"hi"}}]}
     * {"choices":[{"message":{"content":"hi"}}]}閿涘牆鐨弫鏉跨杽閻滃府绱?
     */
    private StreamChunkResult emitStreamData(
            String data,
            ChatGenerateRequest request,
            Consumer<ChatGenerateChunk> onChunk,
            AtomicInteger idx
    ) {
        if (!StringUtils.hasText(data)) {
            return new StreamChunkResult(false, false);
        }
        if ("[DONE]".equals(data)) {
            return new StreamChunkResult(false, true);
        }
        ParsedChunk parsed = parseChunk(data);
        if (parsed == null) {
            return new StreamChunkResult(false, false);
        }
        String sanitizedDelta = sanitizeAssistantDelta(parsed.delta());
        boolean emittedContent = StringUtils.hasText(sanitizedDelta);
        if (emittedContent) {
            onChunk.accept(new ChatGenerateChunk(
                    request.conversationId(),
                    request.clientMessageId(),
                    idx.getAndIncrement(),
                    sanitizedDelta,
                    false,
                    parsed.reasoning(),
                    null));
        }
        return new StreamChunkResult(emittedContent, parsed.done());
    }

    private static void emitDoneChunk(
            ChatGenerateRequest request,
            Consumer<ChatGenerateChunk> onChunk,
            AtomicInteger idx,
            String reasoning
    ) {
        onChunk.accept(new ChatGenerateChunk(
                request.conversationId(), request.clientMessageId(), idx.getAndIncrement(), "", true, reasoning, null));
    }

    ParsedChunk parseChunk(String data) {
        if (!(data.startsWith("{") && data.endsWith("}"))) {
            return new ParsedChunk(data, false, null);
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            if (root.has("error")) {
                throw new IllegalStateException("upstream error");
            }
            JsonNode choices0 = root.path("choices").isArray() && root.path("choices").size() > 0 ? root.path("choices").get(0) : null;
            if (choices0 != null) {
                JsonNode delta = choices0.path("delta");
                String content = extractContentText(delta.path("content"));

                String reasoning = extractContentText(delta.path("reasoning"));
                if (reasoning.isEmpty()) {
                    reasoning = extractContentText(delta.path("reasoning_content"));
                }
                if (reasoning.isEmpty()) {
                    reasoning = extractContentText(choices0.path("reasoning"));
                }

                String finish = choices0.path("finish_reason").asText("");
                boolean done = !finish.isEmpty() && !"null".equalsIgnoreCase(finish);

                String fallback = extractContentText(choices0.path("message").path("content"));
                String deltaText = !content.isEmpty() ? content : fallback;
                return new ParsedChunk(deltaText, done, reasoning.isEmpty() ? null : reasoning);
            }

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode candidate = candidates.get(0);
                String content = extractContentText(candidate.path("content").path("parts"));
                String finish = candidate.path("finishReason").asText(candidate.path("finish_reason").asText(""));
                boolean done = !finish.isEmpty() && !"null".equalsIgnoreCase(finish);
                return new ParsedChunk(content, done, null);
            }

            String outputText = extractContentText(root.path("output_text"));
            return outputText.isEmpty() ? null : new ParsedChunk(outputText, true, null);
        } catch (JsonProcessingException ignore) {
            return new ParsedChunk(data, false, null);
        }
    }

    private static String extractContentText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : node) {
                String part = item.isTextual()
                        ? item.asText("")
                        : firstNonBlank(item.path("text").asText(""), item.path("content").asText(""));
                if (StringUtils.hasText(part)) {
                    text.append(part);
                }
            }
            return text.toString();
        }
        return firstNonBlank(node.path("text").asText(""), node.path("content").asText(""));
    }

    private static String sanitizeAssistantDelta(String raw) {
        String text = raw == null ? "" : raw;
        if (text.isBlank()) {
            return "";
        }
        text = VISION_SPECIAL_TOKEN_PATTERN.matcher(text).replaceAll("");
        text = text.replace("\uFEFF", "");
        text = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]+", "");
        return text;
    }

    record ParsedChunk(String delta, boolean done, String reasoning) {}

    private record StreamChunkResult(boolean emittedContent, boolean done) {}

    private static final class AttemptTelemetry {

        private final Long conversationId;
        private final String clientMessageId;
        private final int attemptNo;
        private final String providerKey;
        private final String routeKey;
        private final String providerSource;
        private final String model;
        private final boolean byok;
        private final LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime firstTokenAt;
        private long asciiCodePoints;
        private long nonAsciiCodePoints;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer httpStatus;

        private AttemptTelemetry(
                ChatGenerateRequest request,
                int attemptNo,
                String providerKey,
                String routeKey,
                String providerSource,
                String model,
                boolean byok
        ) {
            this.conversationId = request == null ? null : request.conversationId();
            this.clientMessageId = request == null ? null : request.clientMessageId();
            this.attemptNo = attemptNo;
            this.providerKey = providerKey;
            this.routeKey = routeKey;
            this.providerSource = providerSource;
            this.model = model;
            this.byok = byok;
        }

        private Long conversationId() {
            return conversationId;
        }

        private void setHttpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        private void observe(ChatGenerateChunk chunk) {
            try {
                String delta = chunk == null ? null : chunk.delta();
                if (!StringUtils.hasText(delta)) {
                    return;
                }
                if (firstTokenAt == null) {
                    firstTokenAt = LocalDateTime.now();
                }
                delta.codePoints().forEach(codePoint -> {
                    if (codePoint <= 0x7f) {
                        asciiCodePoints++;
                    } else {
                        nonAsciiCodePoints++;
                    }
                });
            } catch (Throwable ignored) {
                // Telemetry is deliberately best effort and must never interrupt streaming.
            }
        }

        private void observeUsage(String data, ObjectMapper objectMapper) {
            try {
                if (data == null || !data.startsWith("{") || objectMapper == null) {
                    return;
                }
                JsonNode root = objectMapper.readTree(data);
                JsonNode usage = root.path("usage");
                if (usage.isObject()) {
                    promptTokens = firstNonNegativeInt(usage, "prompt_tokens", "input_tokens");
                    completionTokens = firstNonNegativeInt(usage, "completion_tokens", "output_tokens");
                }
                JsonNode googleUsage = root.path("usageMetadata");
                if (googleUsage.isObject()) {
                    Integer googlePrompt = firstNonNegativeInt(googleUsage, "promptTokenCount");
                    Integer googleCompletion = firstNonNegativeInt(googleUsage, "candidatesTokenCount");
                    promptTokens = googlePrompt == null ? promptTokens : googlePrompt;
                    completionTokens = googleCompletion == null ? completionTokens : googleCompletion;
                }
            } catch (Throwable ignored) {
                // Usage is optional; malformed metrics must not affect the response stream.
            }
        }

        private GenerationAttemptEvent finish(boolean cancelled, Throwable failure) {
            Integer completionValue = completionTokens == null ? estimatedCompletionTokens() : completionTokens;
            boolean completionEstimated = completionTokens == null && completionValue != null;
            String status = cancelled ? "CANCELLED" : (failure == null ? "SUCCESS" : "FAILED");
            String errorCode = cancelled ? "CANCELLED" : (failure == null ? null : telemetryErrorCode(failure, httpStatus));
            return new GenerationAttemptEvent(
                    conversationId,
                    clientMessageId,
                    org.slf4j.MDC.get("traceId"),
                    attemptNo,
                    providerKey,
                    routeKey,
                    providerSource,
                    model,
                    byok,
                    attemptNo > 1,
                    startedAt,
                    firstTokenAt,
                    LocalDateTime.now(),
                    httpStatus,
                    status,
                    errorCode,
                    failure == null ? null : failure.getMessage(),
                    promptTokens,
                    false,
                    completionValue,
                    completionEstimated
            );
        }

        private static Integer firstNonNegativeInt(JsonNode node, String... fieldNames) {
            if (node == null || fieldNames == null) {
                return null;
            }
            for (String fieldName : fieldNames) {
                JsonNode value = node.get(fieldName);
                if (value != null && value.isNumber() && value.canConvertToInt() && value.asInt() >= 0) {
                    return value.asInt();
                }
            }
            return null;
        }

        private Integer estimatedCompletionTokens() {
            if (asciiCodePoints <= 0 && nonAsciiCodePoints <= 0) {
                return null;
            }
            long estimate = ((asciiCodePoints + 3L) / 4L) + nonAsciiCodePoints;
            return estimate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, estimate);
        }
    }
}
