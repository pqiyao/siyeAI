package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only local health snapshot. It intentionally exposes no shell or SQL input. */
@Component
public class AgentSystemInspectionTool {

    private static final Pattern MIGRATION_FILE = Pattern.compile("^V(\\d+)__.*\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIGRATION_VERSION = Pattern.compile("(\\d+)");

    private final AgentAdminMapper agentMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;
    private final Path repositoryRoot;
    private final Path logFile;
    private final Path backupRoot;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    public AgentSystemInspectionTool(
            AgentAdminMapper agentMapper,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir,
            @Value("${app.agent.inspection.repository-root:${user.dir}}") String repositoryDir,
            @Value("${app.agent.inspection.log-file:${user.dir}/logs/sillyspringboot.log}") String logFilePath,
            @Value("${app.agent.inspection.backup-root:}") String backupDir
    ) {
        this.agentMapper = agentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redisProvider = redisProvider;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.repositoryRoot = Path.of(repositoryDir).toAbsolutePath().normalize();
        this.logFile = Path.of(logFilePath).toAbsolutePath().normalize();
        this.backupRoot = backupDir == null || backupDir.isBlank()
                ? null : Path.of(backupDir).toAbsolutePath().normalize();
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
        Map<String, Object> database = migrationSnapshot();
        result.put("database", database);
        result.put("redis", redisSnapshot());
        result.put("storage", storageSnapshot());
        Map<String, Object> backup = backupSnapshot();
        Map<String, Object> build = buildSnapshot();
        Map<String, Object> git = gitSnapshot();
        result.put("backup", backup);
        result.put("build", build);
        result.put("git", git);
        result.put("logs", logSnapshot());
        result.put("releaseReadiness", releaseReadiness(database, backup, build, git));
        return result;
    }

    private Map<String, Object> releaseReadiness(Map<String, Object> database,
                                                 Map<String, Object> backup,
                                                 Map<String, Object> build,
                                                 Map<String, Object> git) {
        List<String> blockers = new ArrayList<>();
        if (!"UP".equals(String.valueOf(database.get("status")))) blockers.add("DATABASE_NOT_READY");
        if (Boolean.TRUE.equals(database.get("migrationVersionMismatch"))) {
            blockers.add("MIGRATION_VERSION_MISMATCH");
        }
        if (!"AVAILABLE".equals(String.valueOf(build.get("status")))) blockers.add("BUILD_ARTIFACT_MISSING");
        if (Boolean.FALSE.equals(build.get("artifactFresh"))) blockers.add("BUILD_ARTIFACT_STALE");
        if ("DIRTY".equals(String.valueOf(git.get("workingTree")))) blockers.add("WORKTREE_DIRTY");
        if (!Boolean.TRUE.equals(backup.get("recoveryReady"))) blockers.add("BACKUP_NOT_RECOVERY_READY");
        return Map.of(
                "status", blockers.isEmpty() ? "READY_FOR_APPROVAL" : "BLOCKED",
                "approvalRequired", true,
                "executionPerformed", false,
                "rollbackPerformed", false,
                "blockers", List.copyOf(blockers),
                "note", "仅聚合只读巡检事实；构建、部署、上传和回滚仍必须通过独立审批与受控发布流程"
        );
    }

    private Map<String, Object> redisSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        StringRedisTemplate redis = redisProvider == null ? null : redisProvider.getIfAvailable();
        result.put("configured", redis != null);
        if (redis == null || redis.getConnectionFactory() == null) {
            result.put("reachable", false);
            result.put("status", "UNAVAILABLE");
            return result;
        }
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            boolean reachable = pong != null && !pong.isBlank();
            result.put("reachable", reachable);
            result.put("status", reachable ? "UP" : "UNAVAILABLE");
        } catch (Exception ex) {
            result.put("reachable", false);
            result.put("status", "UNAVAILABLE");
            result.put("message", clip(ex.getMessage(), 500));
        }
        return result;
    }

    public void saveArtifact(long taskId, long stepId, Map<String, Object> content) {
        String json;
        try {
            json = objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("系统巡检结果格式化失败", e);
        }
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", "system-health");
        artifact.put("artifactType", "REPORT");
        artifact.put("name", "系统健康检查结果");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", "agent://task/" + taskId + "/artifacts/system-health.json");
        artifact.put("contentHash", sha256(json));
        artifact.put("sizeBytes", (long) json.getBytes(StandardCharsets.UTF_8).length);
        artifact.put("mimeType", "application/json");
        artifact.put("metadataJson", "{\"source\":\"system.health_check\"}");
        artifact.put("contentJson", json);
        if (agentMapper.upsertArtifact(artifact) != 1) {
            throw new IllegalStateException("系统巡检结果保存失败");
        }
        agentMapper.refreshArtifactCount(taskId);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** Inspects local build outputs only; it never invokes Maven or another build command. */
    private Map<String, Object> buildSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        Path pom = repositoryRoot.resolve("pom.xml").normalize();
        Path classes = repositoryRoot.resolve("target/classes").normalize();
        Path jar = repositoryRoot.resolve("target/SillySpringboot-0.0.1-SNAPSHOT.jar").normalize();
        result.put("projectRoot", repositoryRoot.toString());
        result.put("pomExists", Files.isRegularFile(pom));
        result.put("classesExists", Files.isDirectory(classes));
        result.put("jarExists", Files.isRegularFile(jar));
        try {
            if (Files.isRegularFile(pom)) {
                result.put("pomLastModified", Files.getLastModifiedTime(pom).toInstant().toString());
            }
            if (Files.isDirectory(classes)) {
                result.put("classesLastModified", Files.getLastModifiedTime(classes).toInstant().toString());
            }
            if (Files.isRegularFile(jar)) {
                result.put("jarSizeBytes", Files.size(jar));
                result.put("jarLastModified", Files.getLastModifiedTime(jar).toInstant().toString());
                result.put("jarSha256", sha256(jar));
                Path original = repositoryRoot.resolve("target/SillySpringboot-0.0.1-SNAPSHOT.jar.original").normalize();
                if (Files.isRegularFile(original)) {
                    result.put("originalJarSizeBytes", Files.size(original));
                    result.put("originalJarSha256", sha256(original));
                }
            }
            boolean classesAvailable = Files.isDirectory(classes);
            boolean jarAvailable = Files.isRegularFile(jar);
            boolean artifactFresh = artifactFresh(classesAvailable, jarAvailable,
                    jarAvailable ? Files.getLastModifiedTime(jar).toMillis() : 0L,
                    classesAvailable ? Files.getLastModifiedTime(classes).toMillis() : 0L);
            result.put("classesAvailable", classesAvailable);
            result.put("jarAvailable", jarAvailable);
            result.put("artifactFresh", artifactFresh);
            result.put("status", classesAvailable && jarAvailable ? "AVAILABLE" : "MISSING");
        } catch (Exception ex) {
            result.put("status", "UNKNOWN");
            result.put("message", ex.getClass().getSimpleName() + ": " + clip(ex.getMessage(), 500));
        }
        return result;
    }

    private static String sha256(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16_384];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    /** Reads only the configured application log file; task input cannot choose a path. */
    private Map<String, Object> logSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuredPath", logFile.toString());
        if (!Files.isRegularFile(logFile)) {
            result.put("status", "UNAVAILABLE");
            result.put("message", "应用日志文件不存在");
            return result;
        }
        try {
            long size = Files.size(logFile);
            result.put("sizeBytes", size);
            if (size > 2_000_000L) {
                result.put("status", "TOO_LARGE");
                result.put("message", "应用日志超过只读摘要大小限制");
                return result;
            }
            List<String> allLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, allLines.size() - 200);
            List<String> tail = allLines.subList(from, allLines.size());
            long errors = tail.stream().filter(line -> line.toUpperCase(Locale.ROOT).contains("ERROR")).count();
            long warnings = tail.stream().filter(line -> line.toUpperCase(Locale.ROOT).contains("WARN")).count();
            result.put("lineCount", allLines.size());
            result.put("tailLineCount", tail.size());
            result.put("errorLineCount", errors);
            result.put("warningLineCount", warnings);
            result.put("tail", tail);
            result.put("status", "UP");
        } catch (Exception ex) {
            result.put("status", "UNAVAILABLE");
            result.put("message", ex.getClass().getSimpleName() + ": " + clip(ex.getMessage(), 500));
        }
        return result;
    }

    /** Executes only fixed, read-only Git commands with a short timeout. */
    private Map<String, Object> gitSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configuredPath", repositoryRoot.toString());
        if (!Files.isDirectory(repositoryRoot)) {
            result.put("status", "UNAVAILABLE");
            result.put("message", "Git 仓库路径不是目录");
            return result;
        }
        try {
            CommandResult root = runGit("rev-parse", "--show-toplevel");
            if (root.exitCode() != 0 || root.stdout().isBlank()) {
                result.put("status", "NOT_A_REPOSITORY");
                result.put("message", clip(root.stderr(), 500));
                return result;
            }
            String topLevel = root.stdout().trim();
            CommandResult status = runGit("status", "--short", "--branch");
            CommandResult commit = runGit("log", "-1", "--format=%H%x09%an%x09%ad%x09%s");
            if (status.exitCode() != 0) {
                result.put("status", "UNAVAILABLE");
                result.put("message", clip(status.stderr(), 500));
                return result;
            }
            List<String> lines = status.stdout().lines().toList();
            String branch = lines.isEmpty() ? "" : lines.get(0).replaceFirst("^##\\s*", "").trim();
            List<String> changes = lines.size() <= 1 ? List.of() : lines.subList(1, Math.min(lines.size(), 101));
            result.put("repository", topLevel);
            result.put("branch", branch);
            result.put("workingTree", changes.isEmpty() ? "CLEAN" : "DIRTY");
            result.put("changedFiles", changes);
            result.put("changedFileCount", Math.max(0, lines.size() - 1));
            result.put("lastCommit", commit.exitCode() == 0 ? parseCommit(commit.stdout()) : Map.of());
            result.put("status", "UP");
        } catch (Exception ex) {
            result.put("status", "UNAVAILABLE");
            result.put("message", ex.getClass().getSimpleName() + ": " + clip(ex.getMessage(), 500));
        }
        return result;
    }

    private CommandResult runGit(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repositoryRoot.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        AtomicReference<String> outputRef = new AtomicReference<>("");
        Thread reader = new Thread(() -> {
            try {
                outputRef.set(readLimited(process.getInputStream()));
            } catch (Exception ignored) {
                outputRef.set("");
            }
        }, "agent-git-output-reader");
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("git 命令超时");
        }
        reader.join(1_000L);
        return new CommandResult(process.exitValue(), outputRef.get(), "");
    }

    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(new LimitedOutputStream(output, 20_000));
        return output.toString(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> parseCommit(String raw) {
        String[] fields = raw.trim().split("\\t", 4);
        Map<String, Object> result = new LinkedHashMap<>();
        if (fields.length > 0) result.put("hash", fields[0]);
        if (fields.length > 1) result.put("author", fields[1]);
        if (fields.length > 2) result.put("date", fields[2]);
        if (fields.length > 3) result.put("subject", fields[3]);
        return result;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {}

    private static final class LimitedOutputStream extends java.io.OutputStream {
        private final ByteArrayOutputStream delegate;
        private final int limit;

        private LimitedOutputStream(ByteArrayOutputStream delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void write(int value) {
            if (delegate.size() < limit) delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            int remaining = limit - delegate.size();
            if (remaining > 0) delegate.write(bytes, offset, Math.min(length, remaining));
        }
    }

    private Map<String, Object> migrationSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"
            );
            result.put("flyway", row);
            result.put("status", Boolean.TRUE.equals(row.get("success")) || "1".equals(String.valueOf(row.get("success"))) ? "UP" : "DEGRADED");
            addSourceMigrationSnapshot(result, row.get("version"));
        } catch (Exception e) {
            result.put("status", "UNKNOWN");
            result.put("message", "无法读取 Flyway 状态：" + e.getClass().getSimpleName());
        }
        return result;
    }

    private void addSourceMigrationSnapshot(Map<String, Object> result, Object installedVersion) {
        Path migrationRoot = repositoryRoot.resolve("src/main/resources/db").normalize();
        try (java.util.stream.Stream<Path> files = Files.walk(migrationRoot)) {
            int sourceMax = files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(MIGRATION_FILE::matcher)
                    .filter(Matcher::matches)
                    .mapToInt(match -> Integer.parseInt(match.group(1)))
                    .max().orElse(0);
            int installed = parseMigrationVersion(installedVersion);
            result.put("sourceMigrationMaxVersion", sourceMax);
            result.put("installedMigrationVersion", installed);
            result.put("migrationVersionMismatch", sourceMax > 0 && installed > 0 && sourceMax > installed);
            result.put("sourceMigrationStatus", sourceMax > 0 ? "AVAILABLE" : "MISSING");
        } catch (Exception ex) {
            result.put("sourceMigrationStatus", "UNKNOWN");
            result.put("sourceMigrationMessage", clip(ex.getClass().getSimpleName() + ": " + ex.getMessage(), 500));
        }
    }

    static int parseMigrationVersion(Object value) {
        if (value == null) return 0;
        Matcher matcher = MIGRATION_VERSION.matcher(String.valueOf(value));
        if (!matcher.find()) return 0;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (RuntimeException ignored) { return 0; }
    }

    static boolean artifactFresh(boolean classesAvailable, boolean jarAvailable,
                                 long jarModifiedMillis, long classesModifiedMillis) {
        return !classesAvailable || (jarAvailable && jarModifiedMillis >= classesModifiedMillis);
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

    /** Reads only a configured backup directory; it never creates, deletes, or restores files. */
    private Map<String, Object> backupSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", backupRoot != null);
        if (backupRoot == null) {
            result.put("status", "NOT_CONFIGURED");
            result.put("recoveryReady", false);
            result.put("message", "未配置 Agent 备份目录");
            return result;
        }
        result.put("path", backupRoot.toString());
        if (!Files.isDirectory(backupRoot)) {
            result.put("status", "MISSING");
            result.put("recoveryReady", false);
            return result;
        }
        try (java.util.stream.Stream<Path> files = Files.list(backupRoot)) {
            Path latest = files.filter(Files::isRegularFile)
                    .filter(AgentSystemInspectionTool::isBackupFile)
                    .max(Comparator.comparing(AgentSystemInspectionTool::lastModifiedSafe))
                    .orElse(null);
            if (latest == null) {
                result.put("status", "EMPTY");
                result.put("recoveryReady", false);
                return result;
            }
            long size = Files.size(latest);
            result.put("latestFile", latest.getFileName().toString());
            result.put("latestSizeBytes", size);
            result.put("latestModified", Files.getLastModifiedTime(latest).toInstant().toString());
            if (size <= 64L * 1024L * 1024L) result.put("latestSha256", sha256(latest));
            result.put("status", size > 0 ? "AVAILABLE" : "EMPTY");
            result.put("recoveryReady", size > 0);
            return result;
        } catch (Exception ex) {
            result.put("status", "UNKNOWN");
            result.put("recoveryReady", false);
            result.put("message", ex.getClass().getSimpleName() + ": " + clip(ex.getMessage(), 500));
            return result;
        }
    }

    private static boolean isBackupFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".sql") || name.endsWith(".dump") || name.endsWith(".bak")
                || name.endsWith(".backup") || name.endsWith(".zip") || name.endsWith(".tar.gz");
    }

    private static java.nio.file.attribute.FileTime lastModifiedSafe(Path path) {
        try { return Files.getLastModifiedTime(path); }
        catch (Exception ignored) { return java.nio.file.attribute.FileTime.fromMillis(0); }
    }
}
