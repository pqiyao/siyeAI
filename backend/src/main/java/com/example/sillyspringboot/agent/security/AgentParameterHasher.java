package com.example.sillyspringboot.agent.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable fingerprint for the parameters shown to a human before a tool approval. */
public final class AgentParameterHasher {

    private AgentParameterHasher() {
    }

    public static String hash(ObjectMapper objectMapper, String toolName, String agentRole,
                              String inputJson, String objective, String constraintsJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", value(toolName));
        payload.put("agentRole", value(agentRole));
        payload.put("inputJson", value(inputJson));
        payload.put("objective", value(objective));
        payload.put("constraintsJson", value(constraintsJson));
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审批参数指纹格式化失败", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
