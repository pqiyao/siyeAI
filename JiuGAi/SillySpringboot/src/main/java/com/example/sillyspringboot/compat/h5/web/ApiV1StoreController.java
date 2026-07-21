package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.billing.service.provider.StorePaymentContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/store")
public class ApiV1StoreController {

    private final StoreService storeService;

    public ApiV1StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping("/overview")
    public ApiV1Result<Map<String, Object>> overview(@RequestParam("clientUid") String clientUid) {
        return ApiV1Result.ok(storeService.overview(clientUid));
    }

    @GetMapping("/products")
    public ApiV1Result<List<Map<String, Object>>> products(
            @RequestParam(name = "type", required = false) String type
    ) {
        return ApiV1Result.ok(storeService.listProducts(type));
    }

    @GetMapping("/orders")
    public ApiV1Result<List<Map<String, Object>>> orders(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        return ApiV1Result.ok(storeService.listOrders(clientUid, limit));
    }

    @PostMapping("/orders/create")
    public ApiV1Result<Map<String, Object>> create(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String clientUid = payload == null ? null : stringValue(payload.get("clientUid"));
        String productCode = payload == null ? null : stringValue(payload.get("productCode"));
        String paymentChannel = payload == null ? null : stringValue(payload.get("paymentChannel"));
        return ApiV1Result.ok(storeService.createOrder(clientUid, productCode, paymentChannel, paymentContext(request)));
    }

    @PostMapping("/orders/pay")
    public ApiV1Result<Map<String, Object>> pay(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String clientUid = payload == null ? null : stringValue(payload.get("clientUid"));
        String orderNo = payload == null ? null : stringValue(payload.get("orderNo"));
        return ApiV1Result.ok(storeService.startOrderPayment(clientUid, orderNo, paymentContext(request)));
    }

    @PostMapping("/orders/mock-pay")
    public ApiV1Result<Map<String, Object>> mockPay(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : stringValue(payload.get("clientUid"));
        String orderNo = payload == null ? null : stringValue(payload.get("orderNo"));
        return ApiV1Result.ok(storeService.mockPay(clientUid, orderNo));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static StorePaymentContext paymentContext(HttpServletRequest request) {
        if (request == null) {
            return StorePaymentContext.empty();
        }
        return new StorePaymentContext(resolveClientIp(request), request.getHeader("User-Agent"));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr()
        );
        if (forwarded == null) {
            return "";
        }
        int commaIndex = forwarded.indexOf(',');
        return commaIndex >= 0 ? forwarded.substring(0, commaIndex).trim() : forwarded.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
