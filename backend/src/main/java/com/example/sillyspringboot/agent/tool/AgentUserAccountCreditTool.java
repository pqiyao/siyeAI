package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.billing.service.WalletLedgerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Approved Agent-only wallet credit; never overwrites balances or performs deductions. */
@Component
public class AgentUserAccountCreditTool {

    private final WalletLedgerService walletLedgerService;

    public AgentUserAccountCreditTool(WalletLedgerService walletLedgerService) {
        this.walletLedgerService = walletLedgerService;
    }

    public Map<String, Object> credit(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = parse(step == null ? null : step.get("inputJson"));
        long userId = number(input.get("userId"));
        int score = bounded(input.get("score"));
        int goldCoin = bounded(input.get("goldCoin"));
        if (userId <= 0) throw new IllegalArgumentException("user.account.credit 需要有效 userId");
        if (score == 0 && goldCoin == 0) throw new IllegalArgumentException("至少需要补发一种余额");
        long taskId = number(task == null ? null : task.get("taskId"));
        long stepId = number(step == null ? null : step.get("stepId"));
        String ref = "task-" + taskId + "-step-" + stepId + "-user-" + userId;
        String note = text(input.get("note"));
        Map<String, Object> result = new LinkedHashMap<>(walletLedgerService.agentCredit(
                userId, score, goldCoin, ref, note.isBlank() ? "Agent 审批后钱包补发" : note
        ));
        result.put("taskId", taskId);
        result.put("stepId", stepId);
        return result;
    }

    private static int bounded(Object value) {
        long number = number(value);
        if (number < 0 || number > 1_000_000) throw new IllegalArgumentException("钱包补发金额超出范围");
        return (int) number;
    }

    private static Map<String, Object> parse(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("user.account.credit 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("user.account.credit 输入 JSON 无效", ex);
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value).trim()); }
        catch (NumberFormatException ex) { return 0L; }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
