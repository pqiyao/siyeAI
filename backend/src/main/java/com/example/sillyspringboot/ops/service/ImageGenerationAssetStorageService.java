package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ImageGenerationAssetStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationAssetStorageService.class);
    private static final String DATA_PREFIX = "data:image/";
    private static final int ABSOLUTE_MAX_IMAGE_BYTES = 64 * 1024 * 1024;

    private final Path storageRoot;
    private final AppImageGenerationProperties properties;

    public ImageGenerationAssetStorageService(
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir,
            AppImageGenerationProperties properties
    ) {
        this.storageRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("generated").normalize();
        this.properties = properties;
    }

    public Map<String, Object> externalize(long userId, String requestId, Map<String, Object> result) {
        Map<String, Object> safeResult = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
        Object rawImages = safeResult.get("images");
        if (!(rawImages instanceof List<?> images) || images.isEmpty()) {
            return safeResult;
        }

        List<Map<String, Object>> storedImages = new ArrayList<>(images.size());
        for (int index = 0; index < images.size(); index++) {
            Object rawImage = images.get(index);
            if (!(rawImage instanceof Map<?, ?> source)) {
                throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回的图片结果格式不正确");
            }
            Map<String, Object> image = new LinkedHashMap<>();
            source.forEach((key, value) -> image.put(String.valueOf(key), value));
            String url = stringValue(image.get("url"));
            if (url.startsWith(DATA_PREFIX)) {
                StoredAsset stored = storeDataUrl(userId, requestId, index, url);
                image.put("url", stored.url());
                image.put("mimeType", stored.mimeType());
                image.put("byteSize", stored.byteSize());
                image.put("storage", "server");
            }
            storedImages.add(image);
        }
        safeResult.put("images", storedImages);
        return safeResult;
    }

    public boolean isReady() {
        try {
            Files.createDirectories(storageRoot);
            return Files.isDirectory(storageRoot) && Files.isWritable(storageRoot);
        } catch (Exception ex) {
            log.warn("Image generation asset storage is unavailable: root={}", storageRoot, ex);
            return false;
        }
    }

    Path storageRoot() {
        return storageRoot;
    }

    private StoredAsset storeDataUrl(long userId, String requestId, int index, String dataUrl) {
        ParsedDataUrl parsed = parseDataUrl(dataUrl);
        int maxBytes = Math.max(1_048_576,
                Math.min(ABSOLUTE_MAX_IMAGE_BYTES, properties.getMaxStoredImageBytes()));
        if (parsed.base64().length() > ((long) maxBytes * 4L / 3L) + 16L) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成图片过大，无法安全保存");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(parsed.base64());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回的图片编码不正确", ex);
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生成图片过大，无法安全保存");
        }
        String detectedMimeType = MediaPayloadValidator.requireImage(bytes, parsed.mimeType());
        String extension = extensionFor(detectedMimeType);
        String userScope = sha256("user:" + userId).substring(0, 16);
        String assetName = sha256(requestId + ":" + index) + "." + extension;
        Path userFolder = storageRoot.resolve(userScope).normalize();
        Path target = userFolder.resolve(assetName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生图结果存储路径不正确");
        }

        Path temporary = null;
        try {
            Files.createDirectories(userFolder);
            temporary = userFolder.resolve(assetName + "." + UUID.randomUUID() + ".tmp");
            Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredAsset(
                    "/uploads/generated/" + userScope + "/" + assetName,
                    detectedMimeType,
                    bytes.length
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "生图结果暂时无法安全保存，请稍后重试", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (Exception cleanupFailure) {
                    log.warn("Failed to remove temporary generated image: path={}", temporary, cleanupFailure);
                }
            }
        }
    }

    private static ParsedDataUrl parseDataUrl(String dataUrl) {
        int separator = dataUrl.indexOf(',');
        if (separator <= DATA_PREFIX.length() || separator >= dataUrl.length() - 1) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回的图片地址格式不正确");
        }
        String metadata = dataUrl.substring(5, separator).toLowerCase(Locale.ROOT);
        if (!metadata.startsWith("image/") || !metadata.endsWith(";base64")) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回的图片地址格式不正确");
        }
        String mimeType = metadata.substring(0, metadata.length() - ";base64".length());
        return new ParsedDataUrl(mimeType, dataUrl.substring(separator + 1));
    }

    private static String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "image/avif" -> "avif";
            default -> "png";
        };
    }

    @Scheduled(initialDelay = 600_000L, fixedDelay = 3_600_000L)
    public void cleanupExpiredAssets() {
        if (!Files.isDirectory(storageRoot)) {
            return;
        }
        Duration ttl = Duration.ofSeconds(Math.max(3_600L, properties.getResultTtlSeconds() + 3_600L));
        long cutoff = System.currentTimeMillis() - ttl.toMillis();
        try (Stream<Path> paths = Files.walk(storageRoot, 2)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                        Files.deleteIfExists(path);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to clean expired generated image: path={}", path, ex);
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to scan generated image storage for cleanup: root={}", storageRoot, ex);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                out.append(String.format("%02x", item & 0xff));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ParsedDataUrl(String mimeType, String base64) {}

    private record StoredAsset(String url, String mimeType, int byteSize) {}
}
