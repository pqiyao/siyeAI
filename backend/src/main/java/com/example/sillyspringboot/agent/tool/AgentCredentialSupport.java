package com.example.sillyspringboot.agent.tool;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Locale;
import java.util.Set;

/** Shared credential and URL-safety helpers. Secrets are never returned to callers. */
public final class AgentCredentialSupport {
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of("key", "api_key", "apikey", "token", "access_token", "auth", "authorization", "secret", "password", "signature", "sig", "credential");
    private AgentCredentialSupport() { }
    public static boolean looksSensitiveQuery(URI uri) {
        if (uri == null || uri.getRawQuery() == null) return false;
        for (String part : uri.getRawQuery().split("&")) {
            String key = part.split("=", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (SENSITIVE_QUERY_KEYS.contains(key)) return true;
        }
        return false;
    }
    public static String redactedUrl(URI uri) {
        if (uri == null) return "";
        if (!looksSensitiveQuery(uri)) return uri.toString();
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), "[REDACTED]", uri.getFragment()).toString();
        } catch (Exception ignored) {
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        }
    }
    public static void applyCredential(HttpRequest.Builder builder, String header, String token) {
        String safeHeader = header == null ? "" : header.trim();
        String safeToken = token == null ? "" : token.trim();
        if (safeToken.isBlank()) return;
        if (safeHeader.isBlank()) safeHeader = "Authorization";
        if (!safeHeader.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}")) throw new IllegalStateException("Agent 凭据 header 名称无效");
        builder.header(safeHeader, safeHeader.equalsIgnoreCase("Authorization") ? "Bearer " + safeToken : safeToken);
    }
    public static String credentialMode(String header, String token) {
        return token == null || token.isBlank() ? "NONE" : (header == null || header.isBlank() ? "BEARER" : "CUSTOM_HEADER");
    }
}
