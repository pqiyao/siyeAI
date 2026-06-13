package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatFailureDetails {

    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int DETAIL_BUDGET = 460;
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\bhttp\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_SECRET_FIELD_PATTERN = Pattern.compile(
            "(\\\"(?:proxy_password|api_key|authorization|token|secret|password)\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ASSIGN_SECRET_PATTERN = Pattern.compile(
            "((?:authorization|api[_-]?key|proxy_password|token|secret|password)\\s*[=:]\\s*)([^\\s,;]+)",
            Pattern.CASE_INSENSITIVE
    );

    record TaskFailure(String errorCode, String errorMessage, Integer httpStatus) {
    }

    private ChatFailureDetails() {
    }

    static TaskFailure fromCode(ErrorCode code, String rawMessage, String traceId) {
        return build(code, rawMessage, null, traceId);
    }

    static TaskFailure fromBusinessException(BusinessException exception, String traceId) {
        ErrorCode code = exception == null ? ErrorCode.INTERNAL_ERROR : exception.getErrorCode();
        String message = exception == null ? null : exception.getMessage();
        return build(code, message, exception, traceId);
    }

    static TaskFailure fromThrowable(ErrorCode code, Throwable throwable, String traceId) {
        String message = throwable == null ? null : throwable.getMessage();
        return build(code, message, throwable, traceId);
    }

    private static TaskFailure build(ErrorCode code, String rawMessage, Throwable throwable, String traceId) {
        ErrorCode safeCode = code == null ? ErrorCode.INTERNAL_ERROR : code;
        String detail = chooseDetail(safeCode, rawMessage, throwable);
        Integer httpStatus = resolveHttpStatus(safeCode, detail, throwable);
        String finalMessage = decorate(safeCode, detail, traceId);
        return new TaskFailure(safeCode.name(), finalMessage, httpStatus);
    }

    private static String chooseDetail(ErrorCode code, String rawMessage, Throwable throwable) {
        String primary = normalize(rawMessage);
        String throwableMessage = normalize(throwable == null ? null : throwable.getMessage());
        Throwable root = rootCause(throwable);
        String rootMessage = normalize(root == null ? null : root.getMessage());
        String causeDetail = firstNonBlank(rootMessage, throwableMessage, shortClassName(root), shortClassName(throwable));

        if (code == ErrorCode.UPSTREAM_ERROR || isGenericUserFacing(primary)) {
            String upstreamLike = firstNonBlank(rootMessage, throwableMessage, causeDetail);
            if (!upstreamLike.isBlank()) {
                return trimTo(upstreamLike, DETAIL_BUDGET);
            }
        }
        if (primary.isBlank()) {
            return trimTo(causeDetail, DETAIL_BUDGET);
        }
        if ((code == ErrorCode.INTERNAL_ERROR || code == ErrorCode.UPSTREAM_ERROR) && isUsefulCause(primary, causeDetail)) {
            return trimTo(primary + " | cause=" + causeDetail, DETAIL_BUDGET);
        }
        return trimTo(primary, DETAIL_BUDGET);
    }

    private static Integer resolveHttpStatus(ErrorCode code, String detail, Throwable throwable) {
        Integer parsed = parseHttpStatus(detail);
        if (parsed != null) {
            return parsed;
        }
        Throwable cursor = throwable;
        while (cursor != null) {
            parsed = parseHttpStatus(cursor.getMessage());
            if (parsed != null) {
                return parsed;
            }
            cursor = cursor.getCause();
        }
        return switch (code) {
            case VALIDATION_FAILED -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case UNAUTHORIZED -> 401;
            case FORBIDDEN -> 403;
            case RATE_LIMITED, SERVICE_BUSY -> 429;
            case UPSTREAM_ERROR -> 502;
            case UNSUPPORTED_OPERATION -> 501;
            default -> 500;
        };
    }

    private static Integer parseHttpStatus(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return null;
        }
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            int status = Integer.parseInt(matcher.group(1));
            return status >= 100 && status <= 599 ? status : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String decorate(ErrorCode code, String detail, String traceId) {
        String safeDetail = normalize(detail);
        String base;
        if (safeDetail.isBlank()) {
            base = code.name();
        } else if (safeDetail.equals(code.name()) || safeDetail.startsWith(code.name() + ":")) {
            base = safeDetail;
        } else {
            base = code.name() + ": " + safeDetail;
        }
        String safeTraceId = normalize(traceId);
        if (!safeTraceId.isBlank() && !"unknown".equalsIgnoreCase(safeTraceId)) {
            base = base + " | traceId=" + safeTraceId;
        }
        return trimTo(base, MAX_MESSAGE_LENGTH);
    }

    private static boolean isGenericUserFacing(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.contains("服务暂时不可用")
                || normalized.contains("请稍后重试")
                || normalized.equalsIgnoreCase("request failed")
                || normalized.equalsIgnoreCase("stream error")
                || normalized.equalsIgnoreCase("network error");
    }

    private static boolean isUsefulCause(String primary, String causeDetail) {
        if (causeDetail.isBlank()) {
            return false;
        }
        if (primary.isBlank()) {
            return true;
        }
        return !primary.equals(causeDetail)
                && !primary.contains(causeDetail)
                && !causeDetail.contains(primary);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String shortClassName(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String name = throwable.getClass().getSimpleName();
        return name == null ? "" : name.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        normalized = JSON_SECRET_FIELD_PATTERN.matcher(normalized).replaceAll("$1***$3");
        normalized = ASSIGN_SECRET_PATTERN.matcher(normalized).replaceAll("$1***");
        return normalized;
    }

    private static String trimTo(String text, int max) {
        String normalized = normalize(text);
        if (normalized.length() <= max) {
            return normalized;
        }
        if (max <= 3) {
            return normalized.substring(0, Math.max(0, max));
        }
        return normalized.substring(0, max - 3) + "...";
    }
}
