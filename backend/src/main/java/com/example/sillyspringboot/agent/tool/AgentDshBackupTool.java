package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** DSH backup inspection bridge; creation and restore remain outside this read-only step. */
@Component
public class AgentDshBackupTool {
    private final AgentSystemInspectionTool inspectionTool;
    private final AgentAdminMapper mapper;

    public AgentDshBackupTool(AgentSystemInspectionTool inspectionTool, AgentAdminMapper mapper) {
        this.inspectionTool = inspectionTool;
        this.mapper = mapper;
    }

    public Map<String, Object> inspect(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> health = inspectionTool.inspect(step, task);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.backup.inspect");
        result.put("status", "SUCCEEDED");
        result.put("backup", health.get("backup"));
        Object backup = health.get("backup");
        if (backup instanceof Map<?, ?> values) {
            Map<String, Object> run = new LinkedHashMap<>();
            run.put("taskId", number(task.get("taskId")));
            run.put("stepId", number(step.get("stepId")));
            run.put("backupType", "FILE_SNAPSHOT");
            run.put("backupPath", text(values.get("path")) + "/" + text(values.get("latestFile")));
            run.put("contentHash", text(values.get("latestSha256")));
            run.put("sizeBytes", number(values.get("latestSizeBytes")));
            boolean verified = !text(values.get("latestSha256")).isBlank() && number(values.get("latestSizeBytes")) > 0;
            run.put("verified", verified ? 1 : 0);
            run.put("restoreTested", 0);
            run.put("status", verified ? "VERIFIED" : "UNAVAILABLE");
            mapper.insertBackupRun(run);
            result.put("verificationRecorded", true);
            result.put("verified", verified);
        }
        return result;
    }

    private static long number(Object value) { return value instanceof Number n ? n.longValue() : 0L; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
