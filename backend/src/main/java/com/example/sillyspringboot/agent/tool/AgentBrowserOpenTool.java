package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.shared.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Read-only browser facade: opens one public page and extracts bounded text and links. */
@Component
public class AgentBrowserOpenTool {

    private static final int MAX_RESPONSE_BYTES = 512_000;
    private static final int MAX_TEXT_CHARS = 20_000;
    private static final int MAX_LINKS = 50;
    private static final int MAX_PAGES_PER_CALL = 3;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("<a\\b[^>]*href\\s*=\\s*([\\\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;
    private final Set<String> allowedHosts;

    public AgentBrowserOpenTool(
            @Value("${app.agent.browser.allowed-hosts:${app.agent.api.allowed-hosts:}}") String allowedHosts
    ) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.allowedHosts = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public MapResult open(Map<String, Object> step) {
        MapInput input = MapInput.parse(step == null ? null : step.get("inputJson"));
        String rawUrl = input.text("url");
        if (rawUrl.isBlank()) throw new IllegalArgumentException("browser.open 需要 url");
        URI uri = OutboundUrlGuard.requirePublicHttpUrl(rawUrl, "browser.open 外部地址不安全");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) throw new IllegalArgumentException("browser.open 域名未配置白名单");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.8")
                    .header("User-Agent", "AiSillyTavern-AgentBrowser/1.0")
                    .GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String html;
            try (InputStream body = response.body()) {
                html = readLimited(body);
            }
            String title = extractTitle(html);
            String text = cleanText(html);
            List<String> links = extractLinks(uri, html);
            return new MapResult(AgentCredentialSupport.redactedUrl(uri), host, response.statusCode(), title, text,
                    html.length() > MAX_RESPONSE_BYTES, links,
                    response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("browser.open 被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("browser.open 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    /** Bounded read-only traversal for research tasks; no clicks, forms, sessions, or writes. */
    public Object read(Map<String, Object> step) {
        MapInput input = MapInput.parse(step == null ? null : step.get("inputJson"));
        JsonNode urls = input.node().get("urls");
        if (urls == null || !urls.isArray()) {
            return open(step);
        }
        if (urls.size() == 0 || urls.size() > MAX_PAGES_PER_CALL) {
            throw new IllegalArgumentException("browser.open 一次最多读取 " + MAX_PAGES_PER_CALL + " 个页面");
        }
        List<MapResult> pages = new ArrayList<>();
        for (JsonNode url : urls) {
            String value = url == null || !url.isTextual() ? "" : url.textValue().trim();
            if (value.isBlank()) throw new IllegalArgumentException("browser.open urls 含有空地址");
            Map<String, Object> pageStep = new LinkedHashMap<>();
            pageStep.put("inputJson", "{\"url\":" + quote(value) + "}");
            pages.add(open(pageStep));
        }
        return Map.of("status", "SUCCEEDED", "pageCount", pages.size(), "pages", List.copyOf(pages));
    }

    private static String quote(String value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("browser.open URL 无法编码", ex);
        }
    }

    private static String extractTitle(String html) {
        Matcher matcher = TITLE.matcher(html);
        return matcher.find() ? cleanText(matcher.group(1)).trim() : "";
    }

    private static String cleanText(String html) {
        String withoutBlocks = html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ");
        String text = TAG.matcher(withoutBlocks).replaceAll(" ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }

    private static List<String> extractLinks(URI base, String html) {
        List<String> result = new ArrayList<>();
        Matcher matcher = LINK.matcher(html);
        while (matcher.find() && result.size() < MAX_LINKS) {
            String raw = matcher.group(2).trim();
            if (raw.isBlank() || raw.startsWith("#") || raw.startsWith("javascript:")) continue;
            try {
                URI link = base.resolve(raw);
                if ("http".equalsIgnoreCase(link.getScheme()) || "https".equalsIgnoreCase(link.getScheme())) {
                    result.add(AgentCredentialSupport.redactedUrl(link));
                }
            } catch (IllegalArgumentException ignored) {
                // Skip malformed links while preserving the page result.
            }
        }
        return List.copyOf(result);
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = MAX_RESPONSE_BYTES + 1 - total;
            if (remaining <= 0) break;
            int accepted = Math.min(read, remaining);
            output.write(buffer, 0, accepted);
            total += accepted;
            if (accepted < read) break;
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    public record MapResult(String url, String host, int statusCode, String title, String text,
                            boolean bodyTruncated, List<String> links, String status) {}

    private record MapInput(JsonNode node) {
        static MapInput parse(Object raw) {
            try {
                JsonNode node = raw == null ? new ObjectMapper().createObjectNode() : new ObjectMapper().readTree(String.valueOf(raw));
                if (node == null || !node.isObject()) throw new IllegalArgumentException("browser.open 输入必须是 JSON 对象");
                return new MapInput(node);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("browser.open 输入 JSON 无效", ex);
            }
        }

        String text(String key) {
            JsonNode value = node.get(key);
            return value == null || value.isNull() ? "" : value.asText("").trim();
        }
    }
}
