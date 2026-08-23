package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.UUID;

/** Stores Agent delivery files below the existing upload resource root. */
@Service
public class AgentArtifactStorageService {

    private final Path agentRoot;

    public AgentArtifactStorageService(
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir
    ) {
        this.agentRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("agent").normalize();
    }

    public StoredFile storeZip(long taskId, byte[] bytes) {
        if (taskId <= 0 || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Agent ZIP 内容不能为空");
        }
        Path folder = agentRoot.resolve(String.valueOf(taskId)).normalize();
        Path target = folder.resolve("task-package.zip").normalize();
        ensureInsideAgentRoot(folder);
        ensureInsideAgentRoot(target);
        Path temporary = folder.resolve("task-package-" + UUID.randomUUID() + ".tmp").normalize();
        try {
            Files.createDirectories(folder);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    "/uploads/agent/" + taskId + "/task-package.zip",
                    bytes.length,
                    sha256(bytes)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Agent ZIP 文件保存失败", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public StoredFile storeAudio(long taskId, String artifactKey, byte[] bytes, String contentType) {
        if (taskId <= 0 || artifactKey == null || !artifactKey.matches("audio-[1-4]")
                || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Agent 音频内容或产物编号不合法");
        }
        String mimeType = MediaPayloadValidator.requireAudio(bytes, contentType);
        String extension = extensionFor(mimeType);
        Path folder = agentRoot.resolve(String.valueOf(taskId)).normalize();
        Path target = folder.resolve(artifactKey + "." + extension).normalize();
        ensureInsideAgentRoot(folder);
        ensureInsideAgentRoot(target);
        Path temporary = folder.resolve(artifactKey + "-" + UUID.randomUUID() + ".tmp").normalize();
        try {
            Files.createDirectories(folder);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    "/uploads/agent/" + taskId + "/" + artifactKey + "." + extension,
                    bytes.length,
                    sha256(bytes)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Agent 音频文件保存失败", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public StoredFile storeHtml(long taskId, byte[] bytes) {
        if (taskId <= 0 || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Agent HTML 内容不能为空");
        }
        Path folder = agentRoot.resolve(String.valueOf(taskId)).normalize();
        Path target = folder.resolve("index.html").normalize();
        ensureInsideAgentRoot(folder);
        ensureInsideAgentRoot(target);
        Path temporary = folder.resolve("index-" + UUID.randomUUID() + ".tmp").normalize();
        try {
            Files.createDirectories(folder);
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(
                    "/uploads/agent/" + taskId + "/index.html",
                    bytes.length,
                    sha256(bytes)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Agent HTML 文件保存失败", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    public Path resolveDownload(long taskId, String uri) {
        if (taskId <= 0 || uri == null || !uri.startsWith("/uploads/agent/")) {
            throw new IllegalArgumentException("Agent 产物下载地址不可用");
        }
        String relative = uri.substring("/uploads/agent/".length());
        if (relative.isBlank() || relative.contains("..") || relative.contains("\\")) {
            throw new IllegalArgumentException("Agent 产物下载地址不可用");
        }
        Path path = agentRoot.resolve(relative).normalize();
        Path taskRoot = agentRoot.resolve(String.valueOf(taskId)).normalize();
        ensureInsideAgentRoot(path);
        if (!path.startsWith(taskRoot) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Agent 产物文件不存在");
        }
        return path;
    }

    private void ensureInsideAgentRoot(Path path) {
        if (!path.startsWith(agentRoot)) {
            throw new IllegalArgumentException("Agent 产物路径不合法");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(String.format("%02x", item));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String extensionFor(String mimeType) {
        String value = mimeType == null ? "" : mimeType.toLowerCase();
        if (value.contains("wav")) return "wav";
        if (value.contains("ogg")) return "ogg";
        if (value.contains("flac")) return "flac";
        if (value.contains("mp4")) return "m4a";
        if (value.contains("webm")) return "webm";
        if (value.contains("aac")) return "aac";
        return "mp3";
    }

    public record StoredFile(String uri, long sizeBytes, String contentHash) {}
}
