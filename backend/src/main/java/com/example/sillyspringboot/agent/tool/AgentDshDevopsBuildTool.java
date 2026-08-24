package com.example.sillyspringboot.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Runs only the configured Spring Boot Maven package command after approval. */
@Component
public class AgentDshDevopsBuildTool {
    private final Path repositoryRoot;
    private final boolean enabled;
    private final long timeoutSeconds;

    public AgentDshDevopsBuildTool(
            @Value("${app.agent.dsh.devops-build.enabled:false}") boolean enabled,
            @Value("${app.agent.inspection.repository-root:${user.dir}}") String repositoryRoot,
            @Value("${app.agent.dsh.devops-build.timeout-seconds:900}") long timeoutSeconds
    ) {
        this.enabled = enabled;
        this.repositoryRoot = Path.of(repositoryRoot).toAbsolutePath().normalize();
        this.timeoutSeconds = Math.max(60L, Math.min(timeoutSeconds, 1800L));
    }

    public Map<String, Object> build(Map<String, Object> step, Map<String, Object> task) {
        if (!enabled) throw new IllegalStateException("DSH DevOps Build 未启用");
        Path wrapper = repositoryRoot.resolve("mvnw.cmd").normalize();
        Path pom = repositoryRoot.resolve("pom.xml").normalize();
        if (!wrapper.startsWith(repositoryRoot) || !pom.startsWith(repositoryRoot)
                || !Files.isRegularFile(wrapper) || !Files.isRegularFile(pom)) {
            throw new IllegalStateException("未找到受控 Spring Boot Maven 工程");
        }
        Instant started = Instant.now();
        Process process = null;
        String output = "";
        try {
            process = new ProcessBuilder(List.of(
                    wrapper.toString(), "-q", "-DskipTests", "package"
            )).directory(repositoryRoot.toFile()).redirectErrorStream(true).start();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Process finalProcess = process;
            Thread reader = new Thread(() -> copyBounded(finalProcess.getInputStream(), bytes), "dsh-devops-build-output");
            reader.setDaemon(true);
            reader.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("受控 Maven 构建超时");
            }
            reader.join(5000L);
            output = clip(bytes.toString(StandardCharsets.UTF_8), 12000);
            if (process.exitValue() != 0) throw new IllegalStateException("Maven 构建失败: " + output);
            Path artifact;
            try (var files = Files.list(repositoryRoot.resolve("target"))) {
                artifact = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
                        .max(Comparator.comparing(AgentDshDevopsBuildTool::modified))
                        .orElseThrow(() -> new IllegalStateException("构建完成但未找到 JAR 制品"));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("operationId", "dsh-op-" + UUID.randomUUID());
            result.put("capability", "dsh.devops.build");
            result.put("status", "SUCCEEDED");
            result.put("repositoryRoot", repositoryRoot.toString());
            result.put("artifact", artifact.toString());
            result.put("artifactSizeBytes", Files.size(artifact));
            result.put("artifactSha256", sha256(artifact));
            result.put("durationMs", Duration.between(started, Instant.now()).toMillis());
            result.put("command", "mvnw.cmd -q -DskipTests package");
            result.put("output", output);
            result.put("approvalRequired", true);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new IllegalStateException("Maven 构建被中断", ex);
        } catch (Exception ex) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            throw ex instanceof IllegalStateException
                    ? (IllegalStateException) ex
                    : new IllegalStateException("DSH DevOps Build 失败", ex);
        }
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0 && output.size() < 12000) {
                output.write(buffer, 0, Math.min(read, 12000 - output.size()));
            }
        } catch (Exception ignored) { }
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ex) { return 0L; }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    private static String clip(String value, int max) {
        return value == null ? "" : value.length() <= max ? value : value.substring(0, max);
    }
}
