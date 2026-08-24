package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Executes short Python snippets only inside a configured, network-isolated Docker image. */
@Component
public class AgentCodeRunnerTool {

    private static final int MAX_CODE_CHARS = 32_000;
    private static final int MAX_OUTPUT_BYTES = 128_000;
    private static final long TIMEOUT_SECONDS = 30L;

    private final ObjectMapper objectMapper;
    private final String image;
    private final String runAsUser;
    private final boolean requireDigest;

    public AgentCodeRunnerTool(
            ObjectMapper objectMapper,
            @Value("${app.agent.code-runner.image:}") String image,
            @Value("${app.agent.code-runner.user:65532:65532}") String runAsUser,
            @Value("${app.agent.code-runner.require-digest:false}") boolean requireDigest
    ) {
        this.objectMapper = objectMapper;
        this.image = image == null ? "" : image.trim();
        this.runAsUser = runAsUser == null ? "65532:65532" : runAsUser.trim();
        this.requireDigest = requireDigest;
    }

    public Map<String, Object> run(Map<String, Object> step) {
        Map<String, Object> input = parseInput(step == null ? null : step.get("inputJson"));
        String code = text(input.get("code"));
        if (code.isBlank() || code.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("code.run 的 code 必须为 1 到 32000 个字符");
        }
        if (image.isBlank() || image.contains(" ") || image.contains("\"") || image.contains("'")
                || (requireDigest && !image.matches(".+@sha256:[0-9a-fA-F]{64}"))
                || !isNonRootUser(runAsUser)) {
            throw new IllegalStateException(requireDigest ? "code.run 必须配置 digest 固定的 Docker 镜像" : "code.run 尚未配置有效 Docker 镜像");
        }
        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm", "--network", "none",
                "--cpus", "1", "--memory", "256m", "--pids-limit", "64",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                "--user", runAsUser, "--ulimit", "nofile=256:256", "--ulimit", "nproc=64:64",
                "--read-only", "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=64m",
                "--workdir", "/tmp",
                image, "python", "-"
        ));
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(code.getBytes(StandardCharsets.UTF_8));
            }
            LimitedReader stdout = new LimitedReader(process.getInputStream());
            LimitedReader stderr = new LimitedReader(process.getErrorStream());
            Thread outThread = new Thread(stdout, "agent-code-stdout");
            Thread errThread = new Thread(stderr, "agent-code-stderr");
            outThread.setDaemon(true);
            errThread.setDaemon(true);
            outThread.start();
            errThread.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("code.run 执行超时");
            }
            outThread.join(2_000L);
            errThread.join(2_000L);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", process.exitValue() == 0 ? "SUCCEEDED" : "FAILED");
            result.put("exitCode", process.exitValue());
            result.put("stdout", stdout.value());
            result.put("stderr", stderr.value());
            result.put("stdoutTruncated", stdout.truncated());
            result.put("stderrTruncated", stderr.truncated());
            result.put("sandbox", Map.of("network", "none", "memory", "256m", "cpus", 1,
                    "pids", 64, "readOnlyRoot", true, "capabilities", "DROP_ALL",
                    "noNewPrivileges", true, "runAsUser", runAsUser,
                    "imagePinned", image.matches(".+@sha256:[0-9a-fA-F]{64}"),
                    "timeoutSeconds", TIMEOUT_SECONDS));
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new IllegalStateException("code.run 被中断", ex);
        } catch (Exception ex) {
            if (process != null && process.isAlive()) process.destroyForcibly();
            throw new IllegalStateException("code.run 调用失败: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private static Map<String, Object> parseInput(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) return Map.of();
        try {
            JsonNode node = new ObjectMapper().readTree(String.valueOf(raw));
            if (!node.isObject()) throw new IllegalArgumentException("code.run 输入必须是 JSON 对象");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().isTextual()
                    ? entry.getValue().textValue() : entry.getValue()));
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("code.run 输入 JSON 无效", ex);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static boolean isNonRootUser(String value) {
        if (value == null || !value.matches("[0-9]{1,10}:[0-9]{1,10}")) return false;
        String[] ids = value.split(":", -1);
        try {
            return Long.parseLong(ids[0]) > 0 && Long.parseLong(ids[1]) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static final class LimitedReader implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile boolean truncated;

        private LimitedReader(InputStream input) { this.input = input; }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[8_192];
                int total = 0;
                int read;
                while ((read = stream.read(buffer)) >= 0) {
                    int remaining = MAX_OUTPUT_BYTES - total;
                    if (remaining <= 0) { truncated = true; break; }
                    int accepted = Math.min(read, remaining);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                    if (accepted < read) { truncated = true; break; }
                }
            } catch (Exception ex) {
                truncated = true;
            }
        }

        private String value() { return output.toString(StandardCharsets.UTF_8); }
        private boolean truncated() { return truncated; }
    }
}
