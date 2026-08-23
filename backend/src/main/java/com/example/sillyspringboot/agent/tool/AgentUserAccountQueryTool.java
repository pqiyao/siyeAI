package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only account, wallet and order summary for operations agents. */
@Component
public class AgentUserAccountQueryTool {

    private final AppH5UserProfileExtMapper profileMapper;
    private final AppPaymentOrderMapper orderMapper;

    public AgentUserAccountQueryTool(AppH5UserProfileExtMapper profileMapper, AppPaymentOrderMapper orderMapper) {
        this.profileMapper = profileMapper;
        this.orderMapper = orderMapper;
    }

    public Map<String, Object> query(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        long userId = number(input.get("userId"));
        if (userId <= 0) throw new IllegalArgumentException("user.account.query 需要有效 userId");
        int limit = Math.max(1, Math.min(50, (int) number(input.getOrDefault("limit", 20))));
        AppH5UserProfileExt profile = profileMapper.findByUserId(userId);
        List<AppPaymentOrder> orders = orderMapper.listByUserId(userId, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("account", account(profile));
        result.put("orders", orders == null ? List.of() : orders.stream().map(this::order).toList());
        result.put("orderCount", orders == null ? 0 : orders.size());
        return result;
    }

    private Map<String, Object> account(AppH5UserProfileExt profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", profile != null);
        result.put("score", profile == null ? 0 : profile.getScore());
        result.put("goldCoin", profile == null ? 0 : profile.getGoldCoin());
        result.put("vipType", profile == null ? 0 : profile.getVipType());
        result.put("vipExpiresAt", profile == null ? null : profile.getVipExpiresAt());
        result.put("status", profile == null ? null : profile.getStatus());
        return result;
    }

    private Map<String, Object> order(AppPaymentOrder order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("productCode", order.getProductCode());
        result.put("productName", order.getProductName());
        result.put("productType", order.getProductType());
        result.put("amountCents", order.getAmountCents());
        result.put("paidAmountCents", order.getPaidAmountCents());
        result.put("status", order.getStatus());
        result.put("paymentChannel", order.getPaymentChannel());
        result.put("createdAt", order.getCreatedAt());
        result.put("paidAt", order.getPaidAt());
        return result;
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.account.query 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue().isNumber() ? entry.getValue().numberValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.account.query 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0L; }
    }
}
