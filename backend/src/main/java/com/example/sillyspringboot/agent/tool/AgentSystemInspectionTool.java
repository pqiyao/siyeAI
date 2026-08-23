package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only local health snapshot. It intentionally exposes no shell or SQL input. */
@Component
public class AgentSystemInspectionTool {

    private final AgentAdminMapper agentMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;

    public AgentSystemInspectionTool(
            AgentAdminMapper agentMapper,
            JdbcTemplate jdbcTemplate,
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir
    ) {
        this.agentMapper = agentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> inspect(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("checkedAt", Instant.now().toString());
        result.put("service", "SillySpringboot");
        result.put("jvm", Map.of(
                "javaVersion", System.getProperty("java.version", ""),
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "maxMemoryBytes", Runtime.getRuntime().maxMemory(),
                "freeMemoryBytes", Runtime.getRuntime().freeMemory()
        ));
        result.put("agentOverview", agentMapper.overview());
        result.put("database", migrationSnapshot());
        result.put("storage", storageSnapshot());
        return result;
    }

    private Map<String, Object> migrationSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
            );
            result.put("flyway", row);
            result.put("status", Boolean.TRUE.equals(row.get("success")) || "1".equals(String.valueOf(row.get("success"))) ? "UP" : "DEGRADED");
        } catch (Exception e) {
            result.put("status", "UNKNOWN");
            result.put("message", "无法读取 Flyway 状态：" + e.getClass().getSimpleName());
        }
        return result;
    }

    private Map<String, Object> storageSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", uploadRoot.toString());
        result.put("exists", Files.isDirectory(uploadRoot));
        try {
            FileStore store = Files.getFileStore(uploadRoot);
            result.put("usableSpaceBytes", store.getUsableSpace());
            result.put("totalSpaceBytes", store.getTotalSpace());
            result.put("status", store.getUsableSpace() > 0 ? "UP" : "DEGRADED");
        } catch (Exception e) {
            result.put("status", "UNKNOWN");
        }
        return result;
    }
}
