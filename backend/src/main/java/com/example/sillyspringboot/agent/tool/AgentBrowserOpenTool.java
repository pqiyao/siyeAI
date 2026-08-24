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
    private static final int MAX_FORMS = 20;
    private static final int MAX_FORM_FIELDS = 30;
    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_PAGES_PER_CALL = 3;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("<a\\b[^>]*href\\s*=\\s*([\\\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FORM = Pattern.compile("<form\\b([^>]*)>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FIELD = Pattern.compile("<(input|textarea|select|button)\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z_:][a-zA-Z0-9:._-]*)\\s*=\\s*([\\\"'])(.*?)\\2", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
            URI finalUri = uri;
            HttpResponse<InputStream> response = null;
            String html = "";
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                HttpRequest request = HttpRequest.newBuilder(finalUri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "text/html,application/xhtml+xml;q=0.9,text/plain;q=0.8")
                        .header("User-Agent", "AiSillyTavern-AgentBrowser/1.0")
                        .GET().build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse("");
                    try (InputStream ignored = response.body()) {
                        // Drain neither redirect bodies nor credentials; only follow Location.
                    }
                    if (location.isBlank()) throw new IllegalStateException("browser.open 重定向缺少 Location");
                    if (redirect == MAX_REDIRECTS) {
                        throw new IllegalStateException("browser.open 重定向次数超过限制");
                    }
                    finalUri = validateAllowedUri(finalUri.resolve(location));
                    continue;
                }
                try (InputStream body = response.body()) {
                    html = readLimited(body);
                }
                break;
            }
            String title = extractTitle(html);
            String text = cleanText(html);
            URI responseUri = finalUri;
            String responseHost = responseUri.getHost().toLowerCase(Locale.ROOT);
            List<String> links = extractLinks(responseUri, html);
            List<Map<String, Object>> forms = extractForms(responseUri, html);
            return new MapResult(AgentCredentialSupport.redactedUrl(responseUri), responseHost, response.statusCode(), title, text,
                    html.length() > MAX_RESPONSE_BYTES, links, forms,
                    response.statusCode() >= 200 && response.statusCode() < 300 ? "SUCCEEDED" : "HTTP_ERROR");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("browser.open 被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("browser.open 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private URI validateAllowedUri(URI candidate) {
        URI safe = OutboundUrlGuard.requirePublicHttpUrl(candidate.toString(), "browser.open 重定向地址不安全");
        String host = safe.getHost() == null ? "" : safe.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) throw new IllegalArgumentException("browser.open 重定向域名未配置白名单");
        return safe;
    }

    /** Bounded read-only traversal for research tasks; no clicks, forms, sessions, or writes. */
    public Object read(Map<String, Object> step) {
        MapInput input = MapInput.parse(step == null ? null : step.get("inputJson"));
        JsonNode followIndexes = input.node().get("followLinkIndexes");
        if (followIndexes != null && !followIndexes.isNull()) {
            MapResult first = open(step);
            List<Integer> indexes = validateFollowIndexes(followIndexes, first.links().size());
            List<MapResult> pages = new ArrayList<>();
            pages.add(first);
            for (Integer index : indexes) {
                Map<String, Object> pageStep = new LinkedHashMap<>();
                pageStep.put("inputJson", "{\"url\":" + quote(first.links().get(index)) + "}");
                pages.add(open(pageStep));
            }
            return Map.of("status", "SUCCEEDED", "pageCount", pages.size(), "pages", List.copyOf(pages));
        }
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

    static List<Integer> validateFollowIndexes(JsonNode indexes, int linkCount) {
        if (indexes == null || !indexes.isArray() || indexes.size() == 0 || indexes.size() > MAX_PAGES_PER_CALL - 1) {
            throw new IllegalArgumentException("browser.open followLinkIndexes 一次最多跟随 " + (MAX_PAGES_PER_CALL - 1) + " 个链接");
        }
        Set<Integer> seen = new java.util.HashSet<>();
        List<Integer> result = new ArrayList<>();
        for (JsonNode indexNode : indexes) {
            if (!indexNode.isInt() || indexNode.intValue() < 0
                    || indexNode.intValue() >= linkCount || !seen.add(indexNode.intValue())) {
                throw new IllegalArgumentException("browser.open followLinkIndexes 包含无效或重复链接索引");
            }
            result.add(indexNode.intValue());
        }
        return List.copyOf(result);
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

    private List<Map<String, Object>> extractForms(URI base, String html) {
        List<Map<String, Object>> result = new ArrayList<>();
        Matcher forms = FORM.matcher(html);
        while (forms.find() && result.size() < MAX_FORMS) {
            String attributes = forms.group(1);
            String action = attribute(attributes, "action");
            URI actionUri;
            try {
                actionUri = base.resolve(action.isBlank() ? base.toString() : action);
            } catch (IllegalArgumentException ex) {
                actionUri = base;
            }
            String actionHost = actionUri.getHost() == null ? "" : actionUri.getHost().toLowerCase(Locale.ROOT);
            Map<String, Object> form = new LinkedHashMap<>();
            form.put("method", attribute(attributes, "method").isBlank()
                    ? "GET" : attribute(attributes, "method").toUpperCase(Locale.ROOT));
            form.put("action", AgentCredentialSupport.redactedUrl(actionUri));
            form.put("actionHostAllowed", allowedHosts.contains(actionHost));
            List<Map<String, Object>> fields = new ArrayList<>();
            Matcher fieldMatcher = FIELD.matcher(forms.group(2));
            while (fieldMatcher.find() && fields.size() < MAX_FORM_FIELDS) {
                String tag = fieldMatcher.group(1).toLowerCase(Locale.ROOT);
                String fieldAttributes = fieldMatcher.group(2);
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("tag", tag);
                field.put("type", attribute(fieldAttributes, "type").isBlank()
                        ? ("input".equals(tag) ? "text" : tag) : attribute(fieldAttributes, "type").toLowerCase(Locale.ROOT));
                field.put("name", attribute(fieldAttributes, "name"));
                field.put("id", attribute(fieldAttributes, "id"));
                field.put("required", hasBooleanAttribute(fieldAttributes, "required"));
                field.put("autocomplete", attribute(fieldAttributes, "autocomplete"));
                fields.add(field);
            }
            form.put("fields", List.copyOf(fields));
            form.put("fieldCount", fields.size());
            result.add(form);
        }
        return List.copyOf(result);
    }

    private static String attribute(String source, String name) {
        Matcher matcher = ATTRIBUTE.matcher(source == null ? "" : source);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) return matcher.group(3).trim();
        }
        return "";
    }

    private static boolean hasBooleanAttribute(String source, String name) {
        if (source == null) return false;
        return Pattern.compile("(?:^|\\s)" + Pattern.quote(name) + "(?:\\s|=|$)", Pattern.CASE_INSENSITIVE)
                .matcher(source).find();
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
                            boolean bodyTruncated, List<String> links, List<Map<String, Object>> forms,
                            String status) {}

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
