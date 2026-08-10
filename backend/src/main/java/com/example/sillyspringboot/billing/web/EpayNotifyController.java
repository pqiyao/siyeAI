package com.example.sillyspringboot.billing.web;

import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.billing.service.provider.EpaySignSupport;
import com.example.sillyspringboot.billing.service.provider.EpayStorePaymentProvider;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/payment/epay")
public class EpayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(EpayNotifyController.class);

    private final EpayStorePaymentProvider epayProvider;
    private final AppPaymentOrderMapper orderMapper;
    private final StoreService storeService;

    public EpayNotifyController(
            EpayStorePaymentProvider epayProvider,
            AppPaymentOrderMapper orderMapper,
            StoreService storeService
    ) {
        this.epayProvider = epayProvider;
        this.orderMapper = orderMapper;
        this.storeService = storeService;
    }

    @GetMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notifyGet(@RequestParam Map<String, String> params) {
        return handleNotify(params);
    }

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notifyPost(@RequestParam Map<String, String> params) {
        return handleNotify(params);
    }

    @GetMapping(value = "/return", produces = MediaType.TEXT_PLAIN_VALUE)
    public String returnGet(@RequestParam Map<String, String> params) {
        // Return URL is display-only; settlement must come from async notify.
        return "ok";
    }

    @PostMapping(value = "/return", produces = MediaType.TEXT_PLAIN_VALUE)
    public String returnPost(@RequestParam Map<String, String> params) {
        return returnGet(params);
    }

    private String handleNotify(Map<String, String> rawParams) {
        Map<String, String> params = normalizeParams(rawParams);
        EpayStorePaymentProvider.ResolvedCredentials credentials = epayProvider.resolveCredentialsForNotify();
        if (!credentials.notifyReady()) {
            log.warn("epay notify rejected: credentials not ready");
            return "fail";
        }
        String sign = params.getOrDefault("sign", "");
        if (!EpaySignSupport.verify(params, credentials.key(), sign)) {
            log.warn("epay notify rejected: bad sign, order={}", params.get("out_trade_no"));
            return "fail";
        }
        String signType = params.getOrDefault("sign_type", "");
        if (!signType.isBlank() && !"MD5".equalsIgnoreCase(signType.trim())) {
            log.warn("epay notify rejected: unsupported sign type, order={}", params.get("out_trade_no"));
            return "fail";
        }
        if (!secureEquals(credentials.pid(), params.get("pid"))) {
            log.warn("epay notify rejected: merchant mismatch, order={}", params.get("out_trade_no"));
            return "fail";
        }

        String tradeStatus = firstNonBlank(params.get("trade_status"), params.get("status"));
        if (!isSuccessStatus(tradeStatus)) {
            return "fail";
        }

        String orderNo = firstNonBlank(params.get("out_trade_no"), "");
        if (orderNo.isBlank()) {
            return "fail";
        }
        AppPaymentOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            log.warn("epay notify order missing: {}", orderNo);
            return "fail";
        }

        int paidCents = EpaySignSupport.parseMoneyToCents(params.get("money"));
        if (paidCents < 0 || paidCents != nvl(order.getAmountCents())) {
            log.warn("epay notify amount mismatch: order={} paid={} expect={}",
                    orderNo, paidCents, order.getAmountCents());
            return "fail";
        }

        String providerTradeNo = firstNonBlank(params.get("trade_no"), params.get("transaction_id"));
        if (providerTradeNo.isBlank()) {
            log.warn("epay notify rejected: provider trade number missing, order={}", orderNo);
            return "fail";
        }
        String payloadHash = sha256Hex(new TreeMap<>(params).toString());
        try {
            storeService.confirmProviderPaid(
                    orderNo,
                    "epay",
                    paidCents,
                    providerTradeNo,
                    payloadHash
            );
        } catch (BusinessException ex) {
            log.warn("epay confirm failed: order={} msg={}", orderNo, ex.getMessage());
            return "fail";
        } catch (DataIntegrityViolationException ex) {
            log.warn("epay notify rejected: provider trade number already settled, order={}", orderNo);
            return "fail";
        }
        return "success";
    }

    private static boolean isSuccessStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        return "TRADE_SUCCESS".equals(value)
                || "SUCCESS".equals(value)
                || "1".equals(value);
    }

    private static Map<String, String> normalizeParams(Map<String, String> raw) {
        Map<String, String> params = new LinkedHashMap<>();
        if (raw == null) {
            return params;
        }
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            params.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return params;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.trim().getBytes(StandardCharsets.UTF_8),
                actual.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
