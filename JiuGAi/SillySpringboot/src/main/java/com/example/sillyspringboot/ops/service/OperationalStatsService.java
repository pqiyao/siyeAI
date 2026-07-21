package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.mapper.AppGenerationStatEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class OperationalStatsService {

    private static final Logger log = LoggerFactory.getLogger(OperationalStatsService.class);
    private static final Set<String> GENERATION_STATUSES = Set.of("QUEUED", "GENERATING", "SUCCESS", "FAILED", "STOPPED");

    private final AppGenerationStatEventMapper generationStatEventMapper;

    public OperationalStatsService(AppGenerationStatEventMapper generationStatEventMapper) {
        this.generationStatEventMapper = generationStatEventMapper;
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

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return GENERATION_STATUSES.contains(value) ? value : "QUEUED";
    }
}
