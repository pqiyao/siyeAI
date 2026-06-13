package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.upload.service.AppUploadedAssetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class H5UploadService {

    private final Path uploadRoot;
    private final AppUploadedAssetService uploadedAssetService;

    public H5UploadService(
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir,
            AppUploadedAssetService uploadedAssetService
    ) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.uploadedAssetService = uploadedAssetService;
    }

    public String saveAndGetUrl(MultipartFile file) {
        Path folder = ensureUploadFolder();
        String ext = guessExt(
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType()
        );
        String name = UUID.randomUUID() + (ext.isEmpty() ? "" : ("." + ext));
        Path target = folder.resolve(name);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/h5/" + name;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "上传失败");
        }
    }

    public String saveOwnedImageAndGetUrl(MultipartFile file, long ownerUserId, String sourceModule) {
        String url = saveImageAndGetUrl(file);
        uploadedAssetService.recordOwnedAsset(ownerUserId, url, "image", sourceModule);
        return url;
    }

    public String saveImageAndGetUrl(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "图片不能为空");
        }
        String ext = guessExt(file.getOriginalFilename(), file.getContentType());
        if (ext.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持 png/jpg/jpeg/webp/gif 图片");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!contentType.isBlank() && !contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持图片上传");
        }
        return saveAndGetUrl(file);
    }

    public String saveAudioAndGetUrl(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "语音文件不能为空");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = guessExt(file.getOriginalFilename(), file.getContentType());
        if (!contentType.isBlank()
                && !contentType.startsWith("audio/")
                && !"application/octet-stream".equals(contentType)
                && !"video/mp4".equals(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持常见音频上传");
        }
        if (ext.isBlank() || !isAudioExt(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持常见音频上传");
        }
        return saveAndGetUrl(file);
    }

    public byte[] readUploadedFileBytes(String url) {
        Path path = resolveUploadedFilePath(url);
        try {
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "参考音频读取失败");
        }
    }

    public String detectUploadedFileContentType(String url) {
        Path path = resolveUploadedFilePath(url);
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null && !contentType.isBlank()) {
                return contentType;
            }
        } catch (Exception ignored) {
        }
        return switch (normalizeKnownExt(path.getFileName().toString())) {
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "ogg" -> "audio/ogg";
            case "aac" -> "audio/aac";
            case "amr" -> "audio/amr";
            case "webm" -> "audio/webm";
            default -> "audio/mpeg";
        };
    }

    private Path ensureUploadFolder() {
        Path folder = uploadRoot.resolve("h5");
        try {
            Files.createDirectories(folder);
            return folder;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "上传目录不可用");
        }
    }

    private Path resolveUploadedFilePath(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (!safeUrl.startsWith("/uploads/h5/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频地址不可用");
        }
        String relative = safeUrl.substring("/uploads/h5/".length());
        if (relative.isBlank() || relative.contains("..")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频地址不可用");
        }
        try {
            Path folder = ensureUploadFolder();
            Path path = folder.resolve(relative).normalize();
            if (!path.startsWith(folder) || !Files.exists(path) || Files.isDirectory(path)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频已不存在");
            }
            return path;
        } catch (InvalidPathException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频地址不可用");
        }
    }

    private static String guessExt(String filename, String contentType) {
        String ext = normalizeKnownExt(filename);
        if (!ext.isEmpty()) {
            return ext;
        }
        String safeContentType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        return switch (safeContentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/mp4", "audio/x-m4a", "video/mp4" -> "m4a";
            case "audio/webm" -> "webm";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/aac", "audio/x-aac" -> "aac";
            case "audio/amr", "audio/3gpp", "audio/amr-wb" -> "amr";
            default -> "";
        };
    }

    private static String normalizeKnownExt(String filename) {
        if (filename == null) {
            return "";
        }
        String s = filename.trim().toLowerCase(Locale.ROOT);
        int i = s.lastIndexOf('.');
        if (i < 0 || i == s.length() - 1) {
            return "";
        }
        String ext = s.substring(i + 1);
        return switch (ext) {
            case "png", "jpg", "jpeg", "webp", "gif",
                    "mp3", "wav", "m4a", "webm", "ogg", "aac", "amr" -> ext;
            default -> "";
        };
    }

    private static boolean isAudioExt(String ext) {
        return switch (ext) {
            case "mp3", "wav", "m4a", "webm", "ogg", "aac", "amr" -> true;
            default -> false;
        };
    }
}
