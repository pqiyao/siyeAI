package com.example.sillyspringboot.config;

import com.example.sillyspringboot.shared.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final int MAX_TRACE_ID_LENGTH = 64;
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = normalizeTraceId(request.getHeader(TRACE_ID_HEADER));
        MDC.put(GlobalExceptionHandler.MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(GlobalExceptionHandler.MDC_TRACE_ID);
        }
    }

    private static String normalizeTraceId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return UUID.randomUUID().toString();
        }
        String value = raw.trim();
        if (value.length() > MAX_TRACE_ID_LENGTH || !SAFE_TRACE_ID.matcher(value).matches()) {
            return UUID.randomUUID().toString();
        }
        return value;
    }
}
