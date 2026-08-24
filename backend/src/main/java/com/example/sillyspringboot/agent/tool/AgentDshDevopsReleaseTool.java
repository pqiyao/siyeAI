package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Performs only local, configured DevOps release actions after Agent approval. */
@Component
public class AgentDshDevopsReleaseTool {
    private final Path repositoryRoot;
    private final Path releaseRoot;
    private final Path deploymentRoot;
    private final String healthUrl;
    private final boolean enabled;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AgentDshDevopsReleaseTool(
            @Value("${app.agent.dsh.devops-release.enabled:false}") boolean enabled,
            @Value("${app.agent.inspection.repository-root:${user.dir}}") String repositoryRoot,
            @Value("${app.agent.dsh.devops-release.release-root:}") String releaseRoot,
            @Value("${app.agent.dsh.devops-release.deployment-root:}") String deploymentRoot,
            @Value("${app.agent.dsh.devops-release.health-url:http://127.0.0.1:8080/actuator/health}") String healthUrl,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.repositoryRoot = Path.of(repositoryRoot).toAbsolutePath().normalize();
        this.releaseRoot = resolveConfigured(releaseRoot, this.repositoryRoot.resolve("target/agent-releases"));
        this.deploymentRoot = resolveConfigured(deploymentRoot, this.repositoryRoot.resolve("target/agent-deployed"));
        this.healthUrl = healthUrl == null ? "" : healthUrl.trim();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> run(String action, Map<String, Object> step, Map<String, Object> task) {
        if (!enabled) throw new IllegalStateException("DSH DevOps 发布未启用");
        return switch (action == null ? "" : action.trim().toLowerCase()) {
            case "publish" -> publish(task);
            case "deploy" -> deploy(task);
            case "health" -> health();
            case "rollback" -> rollback(step, task);
            default -> throw new IllegalArgumentException("DSH DevOps 发布动作无效");
        };
    }

    private Map<String, Object> publish(Map<String, Object> task) {
        Path source = artifactFromCompleted(task, "dsh.devops.build");
        requireUnder(source, repositoryRoot);
        ensureDirectory(releaseRoot);
        String hash = sha256(source);
        Path destination = releaseRoot.resolve(hash + ".jar").normalize();
        requireUnder(destination, releaseRoot);
        copy(source, destination);
        return result("dsh.devops.publish", Map.of("artifact", destination.toString(), "artifactSizeBytes", size(destination), "artifactSha256", hash, "rollbackRef", destination.toString()));
    }

    private Map<String, Object> deploy(Map<String, Object> task) {
        Path source = artifactFromCompleted(task, "dsh.devops.publish");
        requireUnder(source, releaseRoot);
        ensureDirectory(deploymentRoot);
        Path current = deploymentRoot.resolve("current.jar").normalize();
        String rollbackRef = "";
        if (Files.isRegularFile(current)) {
            Path backup = deploymentRoot.resolve("rollback-" + Instant.now().toEpochMilli() + ".jar").normalize();
            copy(current, backup);
            rollbackRef = backup.toString();
        }
        copy(source, current);
        return result("dsh.devops.deploy", Map.of("artifact", current.toString(), "artifactSizeBytes", size(current), "artifactSha256", sha256(current), "rollbackRef", rollbackRef, "deploymentRoot", deploymentRoot.toString()));
    }

    private Map<String, Object> health() {
        if (healthUrl.isBlank()) throw new IllegalStateException("健康检查地址未配置");
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("部署健康检查失败: HTTP " + response.statusCode());
            return result("dsh.devops.health", Map.of("healthUrl", healthUrl, "httpStatus", response.statusCode(), "healthy", true, "response", clip(response.body(), 4000)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("健康检查被中断", ex);
        } catch (Exception ex) {
            throw ex instanceof IllegalStateException ? (IllegalStateException) ex : new IllegalStateException("健康检查失败", ex);
        }
    }

    private Map<String, Object> rollback(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        String ref = text(input.get("rollbackRef"));
        if (ref.isBlank()) ref = text(findLatest(task, "dsh.devops.deploy").get("rollbackRef"));
        if (ref.isBlank()) throw new IllegalArgumentException("回滚引用不能为空");
        Path source = Path.of(ref).toAbsolutePath().normalize();
        requireUnder(source, deploymentRoot);
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("回滚制品不存在");
        ensureDirectory(deploymentRoot);
        Path current = deploymentRoot.resolve("current.jar").normalize();
        copy(source, current);
        return result("dsh.devops.rollback", Map.of("artifact", current.toString(), "artifactSha256", sha256(current), "rollbackRef", source.toString()));
    }

    private Path artifactFromCompleted(Map<String, Object> task, String capability) {
        String artifact = text(findLatest(task, capability).get("artifact"));
        if (artifact.isBlank()) throw new IllegalStateException("缺少前置制品: " + capability);
        Path path = Path.of(artifact).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalStateException("前置制品不存在: " + path);
        return path;
    }

    private Map<String, Object> findLatest(Map<String, Object> task, String capability) {
        Object rows = task == null ? null : task.get("completedSteps");
        if (!(rows instanceof Iterable<?> iterable)) return Map.of();
        Map<String, Object> found = Map.of();
        for (Object row : iterable) {
            if (!(row instanceof Map<?, ?> values) || !capability.equals(text(values.get("toolName")))) continue;
            String output = text(values.get("outputJson"));
            if (output.isBlank()) continue;
            try { found = objectMapper.readValue(output, new TypeReference<Map<String, Object>>() { }); } catch (Exception ignored) { }
        }
        return found;
    }

    private Map<String, Object> result(String capability, Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", capability);
        result.put("status", "SUCCEEDED");
        result.putAll(values);
        result.put("approvalRequired", true);
        return result;
    }

    private static Path resolveConfigured(String configured, Path fallback) { return configured == null || configured.isBlank() ? fallback.toAbsolutePath().normalize() : Path.of(configured).toAbsolutePath().normalize(); }
    private static void ensureDirectory(Path path) { try { Files.createDirectories(path); } catch (Exception ex) { throw new IllegalStateException("无法创建受控目录", ex); } }
    private static void copy(Path source, Path destination) { try { Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING); } catch (Exception ex) { throw new IllegalStateException("制品复制失败", ex); } }
    private static void requireUnder(Path path, Path root) { if (!path.startsWith(root)) throw new IllegalArgumentException("路径超出受控目录"); }
    private static long size(Path path) { try { return Files.size(path); } catch (Exception ex) { return 0L; } }
    private static String sha256(Path path) { try (InputStream input = Files.newInputStream(path)) { var digest = java.security.MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[16384]; int read; while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read); StringBuilder result = new StringBuilder(64); for (byte value : digest.digest()) result.append(String.format("%02x", value)); return result.toString(); } catch (Exception ex) { throw new IllegalStateException("制品哈希失败", ex); } }
    private Map<String, Object> parseInput(Object raw) {
        if (raw instanceof Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            values.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() { });
            } catch (Exception ex) {
                throw new IllegalArgumentException("回滚输入 JSON 无效", ex);
            }
        }
        return Map.of();
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String clip(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max); }
}
