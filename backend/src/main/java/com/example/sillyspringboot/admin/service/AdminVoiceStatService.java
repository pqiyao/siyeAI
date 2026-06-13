package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.AdminVoiceStatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminVoiceStatService {

    private final AdminVoiceStatMapper voiceStatMapper;

    public AdminVoiceStatService(AdminVoiceStatMapper voiceStatMapper) {
        this.voiceStatMapper = voiceStatMapper;
    }

    @Transactional(readOnly = true)
    public long countList(
            String scope,
            String status,
            String providerSource,
            String modelName,
            String errorCode,
            int days
    ) {
        return voiceStatMapper.countList(
                normalizeScope(scope),
                normalizeStatus(status),
                trimToNull(providerSource),
                trimToNull(modelName),
                trimToNull(errorCode),
                cutoffAt(normalizeDays(days))
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPage(
            String scope,
            String status,
            String providerSource,
            String modelName,
            String errorCode,
            int days,
            int pageNum,
            int pageSize
    ) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return voiceStatMapper.listPage(
                normalizeScope(scope),
                normalizeStatus(status),
                trimToNull(providerSource),
                trimToNull(modelName),
                trimToNull(errorCode),
                cutoffAt(normalizeDays(days)),
                (safePage - 1) * safeSize,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(int days) {
        int safeDays = normalizeDays(days);
        Timestamp cutoffAt = cutoffAt(safeDays);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("days", safeDays);
        summary.put("totals", defaultMap(voiceStatMapper.summaryTotals(cutoffAt)));
        summary.put("scopes", voiceStatMapper.summaryByScope(cutoffAt));
        summary.put("topProviders", voiceStatMapper.topProviders(cutoffAt, 8));
        summary.put("topErrors", voiceStatMapper.topErrors(cutoffAt, 8));
        return summary;
    }

    private static Map<String, Object> defaultMap(Map<String, Object> data) {
        return data == null ? Map.of() : data;
    }

    private static int normalizeDays(int days) {
        if (days <= 0) {
            return 7;
        }
        return Math.max(1, Math.min(90, days));
    }

    private static Timestamp cutoffAt(int days) {
        return Timestamp.from(Instant.now().minusSeconds(days * 86400L));
    }

    private static String normalizeScope(String scope) {
        String value = trimToNull(scope);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return ("STT".equals(normalized) || "TTS".equals(normalized)) ? normalized : null;
    }

    private static String normalizeStatus(String status) {
        String value = trimToNull(status);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return ("SUCCESS".equals(normalized) || "FAILED".equals(normalized)) ? normalized : null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
