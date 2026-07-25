package com.example.sillyspringboot.shared.logging;

import java.util.regex.Pattern;

/** Removes credentials from diagnostic text before it reaches persistent logs. */
public final class SensitiveLogSanitizer {

    private static final Pattern JSON_SECRET = Pattern.compile(
            "(\"(?:authorization|proxy_password|api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|password)\"\s*:\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile(
            "(authorization\s*[=:]\s*)(?:(?:Bearer|Basic)\s+)?(?:\"[^\"]*\"|'[^']*'|[^\s,;}&]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ASSIGNED_SECRET = Pattern.compile(
            "((?:authorization|proxy_password|api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|password)\s*[=:]\s*)(?:\"[^\"]*\"|'[^']*'|[^\s,;}&]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile(
            "(\bBearer\s+)[A-Za-z0-9._~+/=-]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OPENAI_STYLE_KEY = Pattern.compile(
            "\b(sk|rk|pk)-[A-Za-z0-9_-]{8,}\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern URI_PASSWORD = Pattern.compile(
            "(https?://[^\s/:@]+:)[^\s/@]+(@)",
            Pattern.CASE_INSENSITIVE
    );

    private SensitiveLogSanitizer() {
    }

    public static String sanitize(String raw, int maxLength) {
        if (raw == null || maxLength <= 0) {
            return "";
        }
        String value = raw
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\s+", " ")
                .trim();
        if (value.isEmpty()) {
            return "";
        }
        value = JSON_SECRET.matcher(value).replaceAll("$1***$3");
        value = AUTHORIZATION_VALUE.matcher(value).replaceAll("$1***");
        value = BEARER_TOKEN.matcher(value).replaceAll("$1***");
        value = ASSIGNED_SECRET.matcher(value).replaceAll("$1***");
        value = OPENAI_STYLE_KEY.matcher(value).replaceAll("$1-***");
        value = URI_PASSWORD.matcher(value).replaceAll("$1***$2");
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
