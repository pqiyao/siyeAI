package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns the preceding read-only step output into a durable JSON report artifact. */
@Component
public class AgentReportTool {

    private final AgentAdminMapper mapper;
    private final AgentEvaluationTool evaluationTool;
    private final ObjectMapper objectMapper;

    public AgentReportTool(AgentAdminMapper mapper, AgentEvaluationTool evaluationTool, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.evaluationTool = evaluationTool;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> render(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        long stepId = number(step.get("stepId"));
        List<Map<String, Object>> artifacts = mapper.listArtifacts(taskId);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", 1);
        report.put("title", task.getOrDefault("taskTitle", "Agent 执行报告"));
        report.put("objective", task.get("taskObjective"));
        report.put("generatedAt", java.time.Instant.now().toString());
        report.put("usage", usageSummary(taskId, task));
        report.put("evaluation", evaluationTool.evaluate(task, stepId));
        Map<String, Object> build = completedToolOutput(task.get("completedSteps"), "dsh.devops.build");
        if (!build.isEmpty()) report.put("devopsBuild", build);
        Map<String, Object> backupCheck = completedToolOutput(task.get("completedSteps"), "dsh.backup.inspect");
        Map<String, Object> secretCheck = completedToolOutput(task.get("completedSteps"), "dsh.secret.status");
        if (!backupCheck.isEmpty() || !secretCheck.isEmpty()) {
            report.put("platformChecks", platformChecks(backupCheck, secretCheck));
        }
        report.put("sourceArtifacts", artifacts == null ? List.of() : artifacts.stream()
                .filter(row -> !"REPORT".equals(String.valueOf(row.get("artifactType")))
                        || !"agent-report".equals(String.valueOf(row.get("artifactKey"))))
                .map(row -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("artifactKey", row.get("artifactKey"));
                    source.put("artifactType", row.get("artifactType"));
                    source.put("name", row.get("name"));
                    source.put("status", row.get("status"));
                    source.put("uri", row.get("uri"));
                    source.put("contentHash", row.get("contentHash"));
                    source.put("sizeBytes", row.get("sizeBytes"));
                    source.put("mimeType", row.get("mimeType"));
                    return source;
                }).toList());
        report.put("summary", summaryFor(task.get("taskType"), artifacts, build, backupCheck, secretCheck));
        String content = json(report);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", "agent-report");
        artifact.put("artifactType", "REPORT");
        artifact.put("name", "Agent 执行报告");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", "agent://task/" + taskId + "/artifacts/agent-report.json");
        artifact.put("contentHash", sha256(content));
        artifact.put("sizeBytes", (long) content.getBytes(StandardCharsets.UTF_8).length);
        artifact.put("mimeType", "application/json");
        artifact.put("metadataJson", "{\"source\":\"artifact.render_report\"}");
        artifact.put("contentJson", content);
        if (mapper.upsertArtifact(artifact) != 1) throw new IllegalStateException("Agent 报告保存失败");
        mapper.refreshArtifactCount(taskId);
        Map<String, Object> persisted = mapper.findArtifact(taskId, "agent-report", 1);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("artifactKey", "agent-report");
        output.put("artifactId", persisted == null ? null : persisted.get("id"));
        output.put("summary", report.get("summary"));
        return output;
    }

    private Object summaryFor(Object taskType, List<Map<String, Object>> artifacts, Map<String, Object> build,
                              Map<String, Object> backupCheck, Map<String, Object> secretCheck) {
        long ready = artifacts == null ? 0 : artifacts.stream().filter(row -> "READY".equals(String.valueOf(row.get("status")))).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", taskType == null ? "GENERAL" : taskType);
        summary.put("readyArtifactCount", ready);
        if (!build.isEmpty()) {
            summary.put("devopsBuildStatus", build.getOrDefault("status", "UNKNOWN"));
            summary.put("devopsArtifact", build.getOrDefault("artifact", ""));
            summary.put("devopsArtifactSizeBytes", build.getOrDefault("artifactSizeBytes", 0));
            summary.put("devopsArtifactSha256", build.getOrDefault("artifactSha256", ""));
        }
        if (!backupCheck.isEmpty() || !secretCheck.isEmpty()) {
            summary.put("platformChecks", platformChecks(backupCheck, secretCheck));
        }
        if (hasArtifact(artifacts, "system-health")) {
            Map<String, Object> health = artifactContent(artifacts, "system-health");
            Map<String, Object> release = map(health.get("releaseReadiness"));
            Map<String, Object> logs = map(health.get("logs"));
            Map<String, Object> monitoring = new LinkedHashMap<>();
            monitoring.put("releaseStatus", release.getOrDefault("status", "UNKNOWN"));
            monitoring.put("blockers", release.getOrDefault("blockers", List.of()));
            monitoring.put("errorLineCount", number(logs.get("errorLineCount")));
            monitoring.put("warningLineCount", number(logs.get("warningLineCount")));
            monitoring.put("readOnly", true);
            summary.put("monitoring", monitoring);
            if (!build.isEmpty()) {
                boolean buildReady = "SUCCEEDED".equalsIgnoreCase(String.valueOf(build.get("status")));
                boolean checksReady = "READY_FOR_APPROVAL".equalsIgnoreCase(String.valueOf(release.get("status")));
                summary.put("manualApprovalStatus", buildReady && checksReady
                        ? "READY_FOR_MANUAL_APPROVAL" : "BLOCKED");
                summary.put("manualApprovalRequired", true);
            }
        }
        boolean operationsReview = "OPERATIONS_REVIEW".equalsIgnoreCase(String.valueOf(taskType))
                || (hasArtifact(artifacts, "system-health") && hasArtifact(artifacts, "analytics-data"));
        if (operationsReview) {
            Map<String, Object> operationSummary = operationsSummary(artifacts);
            summary.put("signals", operationSummary.get("signals"));
            summary.put("recommendations", operationSummary.get("recommendations"));
            summary.put("proposedActions", operationSummary.get("proposedActions"));
        }
        return summary;
    }

    private boolean hasArtifact(List<Map<String, Object>> artifacts, String key) {
        if (artifacts == null) return false;
        return artifacts.stream().anyMatch(row -> key.equals(String.valueOf(row.get("artifactKey"))));
    }

    private Map<String, Object> operationsSummary(List<Map<String, Object>> artifacts) {
        List<String> signals = new java.util.ArrayList<>();
        List<String> recommendations = new java.util.ArrayList<>();
        Map<String, Object> review = artifactContent(artifacts, "analytics-review");
        Object warnings = review.get("warnings");
        if (warnings instanceof List<?> values) {
            for (Object value : values) {
                if (value != null && !String.valueOf(value).isBlank()) signals.add(String.valueOf(value));
            }
        }
        List<Map<String, Object>> proposedActions = new java.util.ArrayList<>();
        Object reviewActions = review.get("proposedActions");
        if (reviewActions instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> item) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    item.forEach((key, itemValue) -> action.put(String.valueOf(key), itemValue));
                    proposedActions.add(action);
                }
            }
        }
        Map<String, Object> analytics = artifactContent(artifacts, "analytics-data");
        Map<String, Object> business = map(analytics.get("businessOverview"));
        long users = number(business.get("totalUsers"));
        long activeConversations = number(business.get("activeConversationsRecent"));
        long paidOrders = number(business.get("totalPaidOrders"));
        long revenueCents = number(business.get("totalRevenueCents"));
        long recentPaidOrders = number(business.get("paidOrdersRecent"));
        long recentRevenueCents = number(business.get("revenueCentsRecent"));
        signals.add("用户总数：" + users + "，当前周期活跃会话：" + activeConversations);
        signals.add("已支付订单：" + paidOrders + "，收入（分）：" + revenueCents);
        signals.add("当前查询周期付费订单：" + recentPaidOrders + "，收入（分）：" + recentRevenueCents);
        if (users > 0 && activeConversations == 0) {
            signals.add("当前周期没有活跃会话，建议核查用户活跃和服务可用性");
            recommendations.add("只读核查用户活跃、服务健康和最近发布记录");
        }
        Map<String, Object> health = artifactContent(artifacts, "system-health");
        Map<String, Object> logs = map(health.get("logs"));
        long errors = number(logs.get("errorLineCount"));
        long warningsCount = number(logs.get("warningLineCount"));
        if (errors > 0) {
            signals.add("应用日志摘要发现错误行：" + errors);
            recommendations.add("只读查看错误堆栈和对应时间段的工具调用审计");
        }
        if (warningsCount > 0) {
            signals.add("应用日志摘要发现警告行：" + warningsCount);
            recommendations.add("只读核对警告是否与供应商失败或降级产物相关");
        }
        addStatusSignal(signals, "数据库", map(health.get("database")).get("status"));
        addStatusSignal(signals, "Redis", map(health.get("redis")).get("status"));
        addStatusSignal(signals, "存储", map(health.get("storage")).get("status"));
        Map<String, Object> backup = map(health.get("backup"));
        addStatusSignal(signals, "备份", backup.get("status"));
        if (!Boolean.TRUE.equals(backup.get("recoveryReady"))) {
            recommendations.add("确认数据库和产物备份存在，并按人工流程验证可恢复性");
        }
        addStatusSignal(signals, "构建产物", map(health.get("build")).get("status"));
        if ("DIRTY".equalsIgnoreCase(String.valueOf(map(health.get("git")).get("workingTree")))) {
            signals.add("Git 工作区存在未提交变更");
            recommendations.add("只读检查变更清单；如需发布、上传或回滚，必须人工批准并走受控发布流程");
        }
        Map<String, Object> release = map(health.get("releaseReadiness"));
        Object releaseStatus = release.get("status");
        if (releaseStatus != null) {
            signals.add("发布准备状态：" + releaseStatus);
            Object blockers = release.get("blockers");
            if (blockers instanceof List<?> values && !values.isEmpty()) {
                recommendations.add("处理发布阻塞项：" + String.join(", ", values.stream().map(String::valueOf).toList()));
            }
        }
        if (recommendations.isEmpty()) {
            recommendations.add("继续保留当前只读观察，下一次任务使用相同时间范围做对比");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("signals", signals);
        summary.put("recommendations", recommendations);
        summary.put("proposedActions", proposedActions);
        summary.put("releaseReadiness", release);
        summary.put("writeActions", List.of("无；所有发布、账户、审核和配置变更都需要单独审批"));
        return summary;
    }

    private Map<String, Object> artifactContent(List<Map<String, Object>> artifacts, String key) {
        if (artifacts == null) return Map.of();
        for (Map<String, Object> artifact : artifacts) {
            if (!key.equals(String.valueOf(artifact.get("artifactKey")))) continue;
            try {
                JsonNode node = objectMapper.readTree(String.valueOf(artifact.get("contentJson")));
                return objectMapper.convertValue(node, Map.class);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Map<String, Object> completedToolOutput(Object completedSteps, String toolName) {
        if (!(completedSteps instanceof Iterable<?> rows)) return Map.of();
        Map<String, Object> found = Map.of();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> values)) continue;
            if (!toolName.equals(String.valueOf(values.get("toolName")))) continue;
            Object raw = values.get("outputJson");
            if (raw == null || String.valueOf(raw).isBlank()) continue;
            try {
                JsonNode node = objectMapper.readTree(String.valueOf(raw));
                if (node != null && node.isObject()) {
                    Map<String, Object> parsed = objectMapper.convertValue(node, Map.class);
                    if (!parsed.isEmpty()) found = parsed;
                }
            } catch (Exception ignored) { }
        }
        return found;
    }

    private Map<String, Object> platformChecks(Map<String, Object> backupCheck, Map<String, Object> secretCheck) {
        Map<String, Object> checks = new LinkedHashMap<>();
        if (!backupCheck.isEmpty()) {
            checks.put("backup", backupCheck.getOrDefault("backup", Map.of()));
        }
        if (!secretCheck.isEmpty()) {
            checks.put("credentialsConfigured", secretCheck.getOrDefault("credentialsConfigured", Map.of()));
            checks.put("secretValuesExposed", secretCheck.getOrDefault("secretValuesExposed", false));
        }
        checks.put("readOnly", true);
        return checks;
    }

    private static void addStatusSignal(List<String> signals, String name, Object status) {
        if (status != null && !status.toString().isBlank()
                && !"UP".equalsIgnoreCase(status.toString())
                && !"AVAILABLE".equalsIgnoreCase(status.toString())) {
            signals.add(name + "状态：" + status);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private Map<String, Object> usageSummary(long taskId, Map<String, Object> task) {
        Map<String, Object> calls = mapper.summarizeToolUsage(taskId);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("taskTokenUsage", task.getOrDefault("tokenUsage", 0));
        usage.put("taskCostAmount", task.getOrDefault("costAmount", 0));
        usage.put("totalCalls", calls == null ? 0 : calls.getOrDefault("totalCalls", 0));
        usage.put("succeededCalls", calls == null ? 0 : calls.getOrDefault("succeededCalls", 0));
        usage.put("failedCalls", calls == null ? 0 : calls.getOrDefault("failedCalls", 0));
        usage.put("toolTokenUsage", calls == null ? 0 : calls.getOrDefault("toolTokenUsage", 0));
        usage.put("toolCostAmount", calls == null ? 0 : calls.getOrDefault("toolCostAmount", 0));
        usage.put("unpricedCalls", calls == null ? 0 : calls.getOrDefault("unpricedCalls", 0));
        return usage;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Agent 报告格式化失败", e); }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) out.append(String.format("%02x", item));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
