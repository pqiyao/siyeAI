package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatImageContentService {

    private static final int MAX_IMAGE_COUNT = 4;
    private static final long MAX_INLINE_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final String H5_UPLOAD_PREFIX = "/uploads/h5/";
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );
    private static final Pattern DATA_URL_PATTERN =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.CASE_INSENSITIVE);

    private final Path h5UploadRoot;

    public ChatImageContentService(@Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadDir) {
        this.h5UploadRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("h5").normalize();
    }

    public List<String> resolveInlineDataUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = imageUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (sanitized.isEmpty()) {
            return List.of();
        }
        if (sanitized.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At most 4 images are allowed per message");
        }
        List<String> out = new ArrayList<>(sanitized.size());
        for (String imageUrl : sanitized) {
            out.add(resolveInlineDataUrl(imageUrl));
        }
        return out;
    }

    private String resolveInlineDataUrl(String rawUrl) {
        String url = stripQueryAndFragment(rawUrl);
        if (url.startsWith("data:image/")) {
            return validateInlineDataUrl(url);
        }
        if (!url.startsWith(H5_UPLOAD_PREFIX)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Only site uploads or inline image data are allowed");
        }
        String fileName = url.substring(H5_UPLOAD_PREFIX.length()).trim();
        if (fileName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid chat image path");
        }
        Path target = h5UploadRoot.resolve(fileName).normalize();
        if (!target.startsWith(h5UploadRoot)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid chat image path");
        }
        try {
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Chat image not found");
            }
            long size = Files.size(target);
            if (size <= 0L) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chat image cannot be empty");
            }
            if (size > MAX_INLINE_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chat image cannot exceed 10MB");
            }
            String mimeType = mimeTypeForFile(target.getFileName().toString());
            byte[] bytes = Files.readAllBytes(target);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read chat image");
        }
    }

    private String validateInlineDataUrl(String dataUrl) {
        Matcher matcher = DATA_URL_PATTERN.matcher(dataUrl == null ? "" : dataUrl.trim());
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid inline image data");
        }
        String mimeType = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported image type");
        }
        String base64 = matcher.group(2);
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length <= 0L) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chat image cannot be empty");
            }
            if (bytes.length > MAX_INLINE_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Chat image cannot exceed 10MB");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid inline image data");
        }
        return "data:" + mimeType + ";base64," + base64;
    }

    private static String stripQueryAndFragment(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        int fragmentIndex = value.indexOf('#');
        if (fragmentIndex >= 0) {
            value = value.substring(0, fragmentIndex);
        }
        return value;
    }

    private static String mimeTypeForFile(String fileName) {
        String lower = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported image type");
    }
}
