package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * H5 兼容：统一包装错误响应。
 * 对 Accept: text/event-stream 的请求，直接返回 SSE error 事件，
 * 避免前端只能看到 HTTP 500。
 */
@RestControllerAdvice(basePackages = "com.example.sillyspringboot.compat.h5.web")
public class ApiV1ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiV1ExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Object handleBusiness(BusinessException exception, HttpServletRequest request) {
        return buildFailureResponse(request, exception == null ? null : exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Object handleAny(Exception exception, HttpServletRequest request) {
        log.error("api v1 unexpected error, method={}, uri={}",
                request == null ? "" : request.getMethod(),
                request == null ? "" : request.getRequestURI(),
                exception);
        return buildFailureResponse(request, "服务暂时不可用，请稍后重试");
    }

    private Object buildFailureResponse(HttpServletRequest request, String message) {
        String safeMessage = safeMessage(message);
        if (acceptsEventStream(request)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(toSseErrorPayload(safeMessage));
        }
        return ApiV1Result.fail(safeMessage);
    }

    private static boolean acceptsEventStream(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/stream");
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求失败，请稍后重试";
        }
        return message.trim();
    }

    private static String toSseErrorPayload(String message) {
        return "event: error\n"
                + "data: {\"message\":\""
                + escapeJson(message)
                + "\"}\n\n";
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\r' -> sb.append("\\r");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
