package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * H5 cannot directly access ST /characters/* when ST auth or IP policy is enabled.
 * This controller proxies assets and provides cacheable thumbnail variants.
 */
@RestController
@RequestMapping("/api/v1/st-assets")
public class ApiV1StAssetProxyController {

    private static final Duration ORIGINAL_BROWSER_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration THUMB_BROWSER_CACHE_TTL = Duration.ofDays(7);
    private static final long MEMORY_CACHE_TTL_MS = Duration.ofMinutes(20).toMillis();
    private static final int MEMORY_CACHE_MAX_ENTRIES = 384;
    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");
    private static final String[] DISK_CACHE_EXTENSIONS = new String[]{"webp", "jpg", "png"};

    private final StClient stClient;
    private final SillyTavernProperties sillyTavernProperties;
    private final Map<String, CachedAsset> memoryCache = Collections.synchronizedMap(
            new LinkedHashMap<String, CachedAsset>(96, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedAsset> eldest) {
                    return size() > MEMORY_CACHE_MAX_ENTRIES;
                }
            }
    );

    public ApiV1StAssetProxyController(StClient stClient, SillyTavernProperties sillyTavernProperties) {
        this.stClient = stClient;
        this.sillyTavernProperties = sillyTavernProperties;
    }

    @GetMapping("/characters/{fileName:.+}")
    public ResponseEntity<byte[]> characterFile(
            @PathVariable("fileName") String fileName,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return returnCharacterAsset(fileName, null, OutputFormat.AUTO, ifNoneMatch, ORIGINAL_BROWSER_CACHE_TTL);
    }

    @GetMapping("/characters-thumb/{fileName:.+}")
    public ResponseEntity<byte[]> characterThumbFile(
            @PathVariable("fileName") String fileName,
            @RequestParam(name = "preset", required = false, defaultValue = "card") String preset,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        ImagePreset imagePreset = ImagePreset.from(preset);
        return returnCharacterAsset(
                fileName,
                imagePreset,
                OutputFormat.fromAccept(accept),
                ifNoneMatch,
                THUMB_BROWSER_CACHE_TTL
        );
    }

    private ResponseEntity<byte[]> returnCharacterAsset(
            String fileName,
            ImagePreset preset,
            OutputFormat outputFormat,
            String ifNoneMatch,
            Duration browserTtl
    ) {
        String safeFileName = normalizeFileName(fileName);
        if (safeFileName.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        CachedAsset cachedAsset = loadCharacterAsset(safeFileName, preset, outputFormat);
        if (cachedAsset == null) {
            return ResponseEntity.notFound().build();
        }
        if (matchesEtag(ifNoneMatch, cachedAsset.etag())) {
            ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.status(304)
                    .cacheControl(CacheControl.maxAge(browserTtl).cachePublic())
                    .eTag(cachedAsset.etag());
            if (preset != null) {
                builder.varyBy(HttpHeaders.ACCEPT);
            }
            return builder.build();
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(browserTtl).cachePublic())
                .eTag(cachedAsset.etag())
                .contentLength(cachedAsset.body().length)
                .contentType(cachedAsset.mediaType());
        if (preset != null) {
            builder.varyBy(HttpHeaders.ACCEPT);
        }
        return builder.body(cachedAsset.body());
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String safe = fileName.trim();
        if (safe.isEmpty() || safe.indexOf("..") >= 0 || safe.indexOf('/') >= 0 || safe.indexOf('\\') >= 0) {
            return "";
        }
        return safe;
    }

    private CachedAsset loadCharacterAsset(String fileName, ImagePreset preset, OutputFormat outputFormat) {
        long now = System.currentTimeMillis();
        Path localPath = resolveLocalCharacterAsset(fileName);
        String cacheKey = buildLocalCacheKey(fileName, preset, outputFormat, localPath);
        CachedAsset cached = getCachedAsset(cacheKey, now);
        if (cached != null) {
            return cached;
        }

        if (preset != null && !cacheKey.isBlank()) {
            CachedAsset diskCached = loadThumbnailFromDisk(cacheKey, now);
            if (diskCached != null) {
                memoryCache.put(cacheKey, diskCached);
                return diskCached;
            }
        }

        byte[] sourceBody = localPath == null ? null : readLocalAsset(localPath);
        boolean loadedFromLocal = sourceBody != null && sourceBody.length > 0;
        if (!loadedFromLocal) {
            sourceBody = stClient.fetchUserDirectoryFile("/characters/" + fileName);
        }
        if (sourceBody == null || sourceBody.length == 0) {
            if (!cacheKey.isBlank()) {
                memoryCache.remove(cacheKey);
            }
            return null;
        }

        if (!loadedFromLocal) {
            cacheKey = buildRemoteCacheKey(fileName, preset, outputFormat, sourceBody);
            cached = getCachedAsset(cacheKey, now);
            if (cached != null) {
                return cached;
            }
            if (preset != null) {
                CachedAsset diskCached = loadThumbnailFromDisk(cacheKey, now);
                if (diskCached != null) {
                    memoryCache.put(cacheKey, diskCached);
                    return diskCached;
                }
            }
        }

        AssetPayload payload = buildAssetPayload(fileName, sourceBody, preset, outputFormat);
        if (payload == null || payload.body() == null || payload.body().length == 0) {
            if (!cacheKey.isBlank()) {
                memoryCache.remove(cacheKey);
            }
            return null;
        }

        CachedAsset loaded = new CachedAsset(
                payload.body(),
                payload.mediaType(),
                DigestUtils.md5DigestAsHex(payload.body()),
                now + MEMORY_CACHE_TTL_MS
        );
        if (!cacheKey.isBlank()) {
            memoryCache.put(cacheKey, loaded);
            if (preset != null) {
                saveThumbnailToDisk(cacheKey, loaded);
            }
        }
        return loaded;
    }

    private CachedAsset getCachedAsset(String cacheKey, long now) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return null;
        }
        CachedAsset cached = memoryCache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) {
            return cached;
        }
        return null;
    }

    private String buildLocalCacheKey(String fileName, ImagePreset preset, OutputFormat outputFormat, Path localPath) {
        if (localPath == null) {
            return "";
        }
        try {
            long size = Files.size(localPath);
            long modifiedAt = Files.getLastModifiedTime(localPath).toMillis();
            return buildCacheKey(fileName, "local:" + size + ":" + modifiedAt, preset, outputFormat);
        } catch (IOException ex) {
            return "";
        }
    }

    private String buildRemoteCacheKey(String fileName, ImagePreset preset, OutputFormat outputFormat, byte[] sourceBody) {
        String sourceHash = DigestUtils.md5DigestAsHex(sourceBody);
        return buildCacheKey(fileName, "remote:" + sourceHash, preset, outputFormat);
    }

    private String buildCacheKey(String fileName, String sourceVersion, ImagePreset preset, OutputFormat outputFormat) {
        String safeOutputFormat = outputFormat == null ? OutputFormat.AUTO.key() : outputFormat.key();
        if (preset == null) {
            return fileName + "|source:" + sourceVersion + "|original";
        }
        return fileName + "|source:" + sourceVersion + "|preset:" + preset.key() + "|format:" + safeOutputFormat;
    }

    private AssetPayload buildAssetPayload(String fileName, byte[] sourceBody, ImagePreset preset, OutputFormat outputFormat) {
        if (preset == null) {
            return new AssetPayload(
                    sourceBody,
                    MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM)
            );
        }
        AssetPayload transformed = transformAsset(sourceBody, preset, outputFormat);
        if (transformed != null && transformed.body() != null && transformed.body().length > 0) {
            return transformed;
        }
        return new AssetPayload(
                sourceBody,
                MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM)
        );
    }

    private AssetPayload transformAsset(byte[] sourceBody, ImagePreset preset, OutputFormat outputFormat) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(sourceBody));
        } catch (IOException ex) {
            return null;
        }
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth = Math.max(1, Math.min(sourceWidth, preset.maxWidth()));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * (targetWidth / (double) sourceWidth)));
        boolean hasAlpha = source.getColorModel() != null && source.getColorModel().hasAlpha();

        BufferedImage output = new BufferedImage(
                targetWidth,
                targetHeight,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!hasAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        try {
            if (outputFormat == OutputFormat.WEBP) {
                byte[] webp = encodeWithWriter(output, "webp", preset.jpegQuality());
                if (webp != null && webp.length > 0) {
                    return new AssetPayload(webp, IMAGE_WEBP);
                }
            }
            if (hasAlpha) {
                return new AssetPayload(encodePng(output), MediaType.IMAGE_PNG);
            }
            return new AssetPayload(encodeJpeg(output, preset.jpegQuality()), MediaType.IMAGE_JPEG);
        } catch (IOException ex) {
            return null;
        }
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        byte[] encoded = encodeWithWriter(image, "jpg", quality);
        if (encoded != null && encoded.length > 0) {
            return encoded;
        }
        ByteArrayOutputStream fallbackOut = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", fallbackOut);
        return fallbackOut.toByteArray();
    }

    private byte[] encodeWithWriter(BufferedImage image, String format, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            return null;
        }

        ImageWriter writer = writers.next();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream imageOut = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(imageOut);
                ImageWriteParam writeParam = writer.getDefaultWriteParam();
                if (writeParam.canWriteCompressed()) {
                    writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    writeParam.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
                }
                writer.write(null, new IIOImage(image, null, null), writeParam);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private Path resolveLocalCharacterAsset(String fileName) {
        String localDataDir = sillyTavernProperties.getLocalDataDir();
        if (localDataDir == null || localDataDir.isBlank()) {
            return null;
        }
        Path baseDir;
        try {
            baseDir = Path.of(localDataDir.trim()).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
        Path[] candidates = new Path[]{
                baseDir.resolve("default-user").resolve("characters").resolve(fileName).normalize(),
                baseDir.resolve("characters").resolve(fileName).normalize()
        };
        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (!absolute.startsWith(baseDir)) {
                continue;
            }
            if (Files.isRegularFile(absolute)) {
                return absolute;
            }
        }
        return null;
    }

    private byte[] readLocalAsset(Path localPath) {
        try {
            return Files.readAllBytes(localPath);
        } catch (IOException ignored) {
            return null;
        }
    }

    private CachedAsset loadThumbnailFromDisk(String cacheKey, long now) {
        Path cacheDir = thumbnailCacheDir();
        String fileStem = diskCacheStem(cacheKey);
        for (String extension : DISK_CACHE_EXTENSIONS) {
            Path candidate = cacheDir.resolve(fileStem + "." + extension).normalize();
            if (!candidate.startsWith(cacheDir) || !Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                byte[] body = Files.readAllBytes(candidate);
                if (body.length == 0) {
                    continue;
                }
                return new CachedAsset(
                        body,
                        mediaTypeFromExtension(extension),
                        DigestUtils.md5DigestAsHex(body),
                        now + MEMORY_CACHE_TTL_MS
                );
            } catch (IOException ignored) {
                // Rebuild the thumbnail if the disk cache entry cannot be read.
            }
        }
        return null;
    }

    private void saveThumbnailToDisk(String cacheKey, CachedAsset cachedAsset) {
        if (cachedAsset == null || cachedAsset.body() == null || cachedAsset.body().length == 0) {
            return;
        }
        Path cacheDir = thumbnailCacheDir();
        String extension = extensionForMediaType(cachedAsset.mediaType());
        String fileStem = diskCacheStem(cacheKey);
        Path target = cacheDir.resolve(fileStem + "." + extension).normalize();
        if (!target.startsWith(cacheDir)) {
            return;
        }
        try {
            Files.createDirectories(cacheDir);
            Path temp = Files.createTempFile(cacheDir, fileStem, ".tmp");
            Files.write(temp, cachedAsset.body());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Disk cache is an optimization only; keep serving from memory if writes fail.
        }
    }

    private Path thumbnailCacheDir() {
        String configured = sillyTavernProperties.getAssetCacheDir();
        Path baseDir;
        if (configured == null || configured.isBlank()) {
            baseDir = Path.of(System.getProperty("java.io.tmpdir"), "sillyspringboot", "st-asset-cache");
        } else {
            baseDir = Path.of(configured.trim());
        }
        return baseDir.resolve("characters-thumb").toAbsolutePath().normalize();
    }

    private String diskCacheStem(String cacheKey) {
        return DigestUtils.md5DigestAsHex(cacheKey.getBytes(StandardCharsets.UTF_8));
    }

    private String extensionForMediaType(MediaType mediaType) {
        if (mediaType != null) {
            String value = mediaType.toString().toLowerCase(Locale.ROOT);
            if (value.contains("webp")) {
                return "webp";
            }
            if (value.contains("png")) {
                return "png";
            }
        }
        return "jpg";
    }

    private MediaType mediaTypeFromExtension(String extension) {
        if ("webp".equalsIgnoreCase(extension)) {
            return IMAGE_WEBP;
        }
        if ("png".equalsIgnoreCase(extension)) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.IMAGE_JPEG;
    }

    private boolean matchesEtag(String headerValue, String etag) {
        if (headerValue == null || headerValue.isBlank() || etag == null || etag.isBlank()) {
            return false;
        }
        String[] tokens = headerValue.split(",");
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.startsWith("W/")) {
                normalized = normalized.substring(2).trim();
            }
            if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            if (etag.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private record CachedAsset(byte[] body, MediaType mediaType, String etag, long expiresAt) {
    }

    private record AssetPayload(byte[] body, MediaType mediaType) {
    }

    private enum OutputFormat {
        AUTO("auto"),
        WEBP("webp");

        private final String key;

        OutputFormat(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }

        static OutputFormat fromAccept(String accept) {
            if (accept == null || accept.isBlank()) {
                return AUTO;
            }
            String normalized = accept.toLowerCase(Locale.ROOT);
            if (normalized.contains("image/webp") && ImageIO.getImageWritersByFormatName("webp").hasNext()) {
                return WEBP;
            }
            return AUTO;
        }
    }

    private enum ImagePreset {
        AVATAR("avatar", 160, 0.80f),
        CARD("card", 480, 0.82f),
        DETAIL("detail", 720, 0.86f);

        private final String key;
        private final int maxWidth;
        private final float jpegQuality;

        ImagePreset(String key, int maxWidth, float jpegQuality) {
            this.key = key;
            this.maxWidth = maxWidth;
            this.jpegQuality = jpegQuality;
        }

        String key() {
            return key;
        }

        int maxWidth() {
            return maxWidth;
        }

        float jpegQuality() {
            return jpegQuality;
        }

        static ImagePreset from(String value) {
            if (value == null || value.isBlank()) {
                return CARD;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ImagePreset preset : values()) {
                if (preset.key.equals(normalized)) {
                    return preset;
                }
            }
            return CARD;
        }
    }
}
