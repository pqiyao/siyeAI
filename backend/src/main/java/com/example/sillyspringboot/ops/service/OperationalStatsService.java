package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.mapper.AppGenerationStatEventMapper;
import com.example.sillyspringboot.ops.mapper.AppVoiceStatEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class OperationalStatsService {

    private static final Logger log = LoggerFactory.getLogger(OperationalStatsService.class);
    private static final Set<String> GENERATION_STATUSES = Set.of("QUEUED", "GENERATING", "SUCCESS", "FAILED", "STOPPED");
    private static final Set<String> VOICE_SCOPES = Set.of("STT", "TTS");
    private static final Set<String> VOICE_STATUSES = Set.of("SUCCESS", "FAILED");

    private final AppGenerationStatEventMapper generationStatEventMapper;
    private final AppVoiceStatEventMapper voiceStatEventMapper;

    public OperationalStatsService(
            AppGenerationStatEventMapper generationStatEventMapper,
            AppVoiceStatEventMapper voiceStatEventMapper
    ) {
        this.generationStatEventMapper = generationStatEventMapper;
        this.voiceStatEventMapper = voiceStatEventMapper;
    }

    public void recordGenerationTaskStatus(long taskId, String status) {
        if (taskId <= 0) {
            return;
        }
        String normalizedStatus = normalizeStatus(status);
        try {
            generationStatEventMapper.upsertTaskStatus(taskId, normalizedStatus);
        } catch (Exception ex) {
            log.warn("generation stat write failed taskId={}, status={}, cause={}", taskId, normalizedStatus, ex.toString());
        }
    }

    public void recordSttSuccess(long userId, String providerSource, String modelName, int latencyMs, int audioBytes, int textChars) {
        recordVoiceEvent(
                userId,
                "STT",
                providerSource,
                modelName,
                "",
                "",
                "SUCCESS",
                "",
                textChars,
                audioBytes,
                latencyMs,
                false,
                false,
                ""
        );
    }

    public void recordSttFailure(long userId, String providerSource, String modelName, int latencyMs, String errorCode) {
        recordVoiceEvent(
                userId,
                "STT",
                providerSource,
                modelName,
                "",
                "",
                "FAILED",
                "",
                0,
                0,
                latencyMs,
                false,
                false,
                normalizeErrorCode(errorCode)
        );
    }

    public void recordTtsSuccess(
            long userId,
            String providerSource,
            String modelName,
            String voiceName,
            String templateCode,
            String inputText,
            int latencyMs,
            int audioBytes,
            boolean cacheHit
    ) {
        String fingerprint = buildSpeechFingerprint(providerSource, modelName, voiceName, templateCode, inputText);
        boolean duplicateRequest = !cacheHit
                && StringUtils.hasText(fingerprint)
                && countSuccessfulFingerprintQuietly("TTS", fingerprint) > 0;
        recordVoiceEvent(
                userId,
                "TTS",
                providerSource,
                modelName,
                voiceName,
                templateCode,
                "SUCCESS",
                fingerprint,
                safeLength(inputText),
                audioBytes,
                latencyMs,
                cacheHit,
                duplicateRequest,
                ""
        );
    }

    public void recordTtsFailure(
            long userId,
            String providerSource,
            String modelName,
            String voiceName,
            String templateCode,
            String inputText,
            int latencyMs,
            String errorCode
    ) {
        recordVoiceEvent(
                userId,
                "TTS",
                providerSource,
                modelName,
                voiceName,
                templateCode,
                "FAILED",
                buildSpeechFingerprint(providerSource, modelName, voiceName, templateCode, inputText),
                safeLength(inputText),
                0,
                latencyMs,
                false,
                false,
                normalizeErrorCode(errorCode)
        );
    }

    private void recordVoiceEvent(
            long userId,
            String scope,
            String providerSource,
            String modelName,
            String voiceName,
            String templateCode,
            String status,
            String requestFingerprint,
            int textChars,
            int audioBytes,
            int latencyMs,
            boolean cacheHit,
            boolean duplicateRequest,
            String errorCode
    ) {
        String normalizedScope = normalizeVoiceScope(scope);
        String normalizedStatus = normalizeVoiceStatus(status);
        try {
            voiceStatEventMapper.insertEvent(
                    userId > 0 ? userId : null,
                    normalizedScope,
                    safe(providerSource, 32),
                    safe(modelName, 255),
                    safe(voiceName, 255),
                    safe(templateCode, 64),
                    normalizedStatus,
                    safe(requestFingerprint, 64),
                    clampInt(textChars),
                    clampInt(audioBytes),
                    clampInt(latencyMs),
                    cacheHit,
                    duplicateRequest,
                    safe(normalizeErrorCode(errorCode), 64)
            );
        } catch (Exception ex) {
            log.warn("voice stat write failed scope={}, status={}, provider={}, model={}, cause={}",
                    normalizedScope, normalizedStatus, providerSource, modelName, ex.toString());
        }
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return GENERATION_STATUSES.contains(value) ? value : "QUEUED";
    }

    private long countSuccessfulFingerprintQuietly(String scope, String requestFingerprint) {
        try {
            return voiceStatEventMapper.countSuccessfulFingerprint(scope, requestFingerprint);
        } catch (Exception ex) {
            log.warn("voice stat fingerprint lookup failed scope={}, cause={}", scope, ex.toString());
            return 0L;
        }
    }

    private static String buildSpeechFingerprint(
            String providerSource,
            String modelName,
            String voiceName,
            String templateCode,
            String inputText
    ) {
        String normalizedText = normalizeText(inputText);
        if (!StringUtils.hasText(normalizedText)) {
            return "";
        }
        String raw = safe(providerSource, 32) + "|" +
                safe(modelName, 255) + "|" +
                safe(voiceName, 255) + "|" +
                safe(templateCode, 64) + "|" +
                normalizedText;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static int safeLength(String value) {
        return clampInt(normalizeText(value).length());
    }

    private static int clampInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static String normalizeVoiceScope(String scope) {
        String value = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
        return VOICE_SCOPES.contains(value) ? value : "TTS";
    }

    private static String normalizeVoiceStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return VOICE_STATUSES.contains(value) ? value : "FAILED";
    }

    private static String normalizeErrorCode(String errorCode) {
        if (errorCode == null) {
            return "";
        }
        String value = errorCode.trim();
        if (value.isBlank()) {
            return "";
        }
        int firstBlank = value.indexOf(' ');
        String normalized = firstBlank > 0 ? value.substring(0, firstBlank) : value;
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String safe(String value, int maxLen) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }
}
