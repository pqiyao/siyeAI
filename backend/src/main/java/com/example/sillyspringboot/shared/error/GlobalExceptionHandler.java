package com.example.sillyspringboot.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static final String MDC_TRACE_ID = "traceId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> fields.put(err.getField(), err.getDefaultMessage()));
        String traceId = traceId();
        log.warn("validation failed traceId={} fields={}", traceId, fields.keySet());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppErrorResponse.withFieldErrors(
                        ErrorCode.VALIDATION_FAILED,
                        "请求参数不合法",
                        traceId,
                        fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AppErrorResponse> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> fields = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> a,
                        LinkedHashMap::new));
        String traceId = traceId();
        log.warn("constraint violation traceId={}", traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppErrorResponse.withFieldErrors(
                        ErrorCode.VALIDATION_FAILED,
                        "请求参数不合法",
                        traceId,
                        fields));
    }

    /**
     * 开发时 webpack HMR 会请求 /sockjs-node/info，若误打到本服务则无需记 ERROR 堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex) {
        String path = ex.getResourcePath() == null ? "" : ex.getResourcePath();
        if (path.contains("sockjs-node")) {
            return ResponseEntity.notFound().build();
        }
        String traceId = traceId();
        log.warn("not found traceId={} path={}", traceId, path);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AppErrorResponse.of(ErrorCode.NOT_FOUND, "资源不存在", traceId));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<AppErrorResponse> handleBusiness(BusinessException ex) {
        String traceId = traceId();
        HttpStatus status = mapBusinessStatus(ex.getErrorCode());
        // 商用品：对外 message 允许更“友好/稳定”，错误细节只进日志。
        if (ex.getErrorCode() == ErrorCode.UPSTREAM_ERROR) {
            log.warn("upstream error traceId={} code={} msg={}", traceId, ex.getErrorCode(), ex.getMessage(), ex);
            return ResponseEntity.status(status)
                    .body(AppErrorResponse.of(ex.getErrorCode(), "服务暂时不可用，请稍后重试", traceId));
        }
        log.warn("business error traceId={} code={} msg={}", traceId, ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(AppErrorResponse.of(ex.getErrorCode(), ex.getMessage(), traceId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleStatus(ResponseStatusException ex) {
        // 例如调试接口 token 不正确等：属于预期 4xx，不应刷 ERROR 堆栈。
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is4xxClientError()) {
            return ResponseEntity.status(status).build();
        }
        String traceId = traceId();
        log.warn("status error traceId={} status={} reason={}", traceId, status.value(), ex.getReason());
        return ResponseEntity.status(status).body(AppErrorResponse.of(ErrorCode.UPSTREAM_ERROR, "请求失败", traceId));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<AppErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        String traceId = traceId();
        String method = request == null ? "" : request.getMethod();
        String path = request == null ? "" : request.getRequestURI();
        log.warn("method not supported traceId={} method={} path={} supported={}",
                traceId, method, path, ex.getSupportedMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(AppErrorResponse.of(ErrorCode.VALIDATION_FAILED, "request method is not supported", traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppErrorResponse> handleAny(Exception ex) {
        String traceId = traceId();
        log.error("unhandled error traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppErrorResponse.of(
                        ErrorCode.INTERNAL_ERROR,
                        "服务暂时不可用，请稍后重试",
                        traceId));
    }

    private static HttpStatus mapBusinessStatus(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RATE_LIMITED, SERVICE_BUSY -> HttpStatus.TOO_MANY_REQUESTS;
            case UPSTREAM_ERROR -> HttpStatus.BAD_GATEWAY;
            case UNSUPPORTED_OPERATION -> HttpStatus.NOT_IMPLEMENTED;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static String traceId() {
        String id = MDC.get(MDC_TRACE_ID);
        return id != null ? id : "unknown";
    }
}
