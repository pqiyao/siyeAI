package com.example.sillyspringboot.integration.sillytavern;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * 仅 {@link StClient} 读取；业务层通过 {@link StAdapter}，禁止直接注入本配置去拼 URL。
 */
@Validated
@ConfigurationProperties(prefix = "sillytavern")
public class SillyTavernProperties {

    @NotNull
    private URI baseUrl = URI.create("http://127.0.0.1:8000");

    /**
     * 返回给浏览器/H5 的 ST 根地址（拼头像 URL）。手机访问开发机时填电脑局域网地址，例如 {@code http://192.168.1.8:8000}；
     * 服务端请求 ST 仍用 {@link #baseUrl}。留空则与 baseUrl 相同。
     */
    private String publicBaseUrl = "";

    /** 可选：ST 本地 data 根目录；配置后可优先直读角色图片文件，减少一次 HTTP 代理。 */
    private String localDataDir = "";

    /** Optional disk cache for generated ST character thumbnails. */
    private String assetCacheDir = "";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(120);

    /** 可选；若 ST 侧启用 API Key / Bearer，由 StClient 统一加头 */
    private String apiKey = "";

    /**
     * 阶段 4：ST `/api/backends/chat-completions/generate` 需要的最小默认参数。
     * <p>
     * 这些参数仅允许被 {@link StClient} 使用，业务层禁止关心 ST 的“source/model/代理”等细节。
     */
    private String chatCompletionSource = "openrouter";

    /** 默认模型（交由 ST 后端决定是否可用） */
    private String defaultModel = "deepseek/deepseek-chat";

    /** 若需要让 ST 走反代/自建网关，可配置 reverse_proxy */
    private String reverseProxy = "";

    /** 若 reverse_proxy 需要密码（例如 ST 的 proxy_password 语义），配置此处 */
    private String proxyPassword = "";

    /** OpenRouter: 是否请求返回 reasoning（若上游支持） */
    private boolean includeReasoning = true;

    /** OpenRouter / OpenAI: reasoning effort（不同模型支持不同，留空则不发送） */
    private String reasoningEffort = "";

    /** ST chats API 需要的 avatar_url 默认值（运营期可配置/可替换） */
    private String defaultAvatarUrl = "default_Assistant.png";

    /**
     * 长期模式（方案 A）：生成参数优先跟随 ST settings.json（同源 preset），避免网关维护一套独立 preset。
     * <p>
     * 当启用时，temperature/max_tokens/top_p/frequency/presence 等优先从 ST `/api/settings/get` 的 settings 字段解析。
     */
    private boolean preferStPresetParams = true;

    /**
     * 仅用于开发/联调：抓取网关发往 ST `/generate` 的请求体，做 Golden Diff。
     * 默认关闭，避免在生产环境泄漏 prompt / 用户内容。
     */
    private Debug debug = new Debug();

    public static class Debug {
        /** 是否启用抓取与日志 */
        private boolean enabled = false;
        /**
         * 访问调试接口所需 token。为空则禁用调试接口（即便 enabled=true 也仅记录日志，不提供 HTTP 读取）。
         * 建议只在本机环境设置。
         */
        private String token = "";
        /** 内存保留最近 N 条请求体（用于接口读取） */
        private int bufferSize = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token == null ? "" : token;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl;
    }

    /** H5 可见的 ST 根（头像、静态资源），未配置时退回 baseUrl */
    public URI resolvePublicBaseUri() {
        String raw = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (!raw.isEmpty()) {
            return URI.create(raw);
        }
        return baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getLocalDataDir() {
        return localDataDir;
    }

    public void setLocalDataDir(String localDataDir) {
        this.localDataDir = localDataDir == null ? "" : localDataDir;
    }

    public String getAssetCacheDir() {
        return assetCacheDir;
    }

    public void setAssetCacheDir(String assetCacheDir) {
        this.assetCacheDir = assetCacheDir == null ? "" : assetCacheDir;
    }

    public String getChatCompletionSource() {
        return chatCompletionSource;
    }

    public void setChatCompletionSource(String chatCompletionSource) {
        this.chatCompletionSource = chatCompletionSource;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getReverseProxy() {
        return reverseProxy;
    }

    public void setReverseProxy(String reverseProxy) {
        this.reverseProxy = reverseProxy;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isIncludeReasoning() {
        return includeReasoning;
    }

    public void setIncludeReasoning(boolean includeReasoning) {
        this.includeReasoning = includeReasoning;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getDefaultAvatarUrl() {
        return defaultAvatarUrl;
    }

    public void setDefaultAvatarUrl(String defaultAvatarUrl) {
        this.defaultAvatarUrl = defaultAvatarUrl;
    }

    public boolean isPreferStPresetParams() {
        return preferStPresetParams;
    }

    public void setPreferStPresetParams(boolean preferStPresetParams) {
        this.preferStPresetParams = preferStPresetParams;
    }

    public Debug getDebug() {
        return debug;
    }

    public void setDebug(Debug debug) {
        this.debug = debug == null ? new Debug() : debug;
    }
}
