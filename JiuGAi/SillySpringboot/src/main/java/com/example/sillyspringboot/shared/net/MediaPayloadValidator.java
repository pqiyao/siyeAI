package com.example.sillyspringboot.shared.net;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MediaPayloadValidator {

    private MediaPayloadValidator() {
    }

    public static String requireAudio(byte[] bytes, String contentType) {
        String detected = detectAudio(bytes);
        String declared = normalizeContentType(contentType);
        if (detected.isBlank()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音平台返回的不是有效音频");
        }
        if (!declared.isBlank() && !declared.startsWith("audio/")
                && !"application/octet-stream".equals(declared)) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "语音平台返回了错误的内容类型");
        }
        return detected;
    }

    public static String requireImage(byte[] bytes, String contentType) {
        String detected = detectImage(bytes);
        String declared = normalizeContentType(contentType);
        if (detected.isBlank()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回的不是有效图片");
        }
        if (!declared.isBlank() && !declared.startsWith("image/")
                && !"application/octet-stream".equals(declared)) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "生图平台返回了错误的内容类型");
        }
        return detected;
    }

    private static String detectAudio(byte[] bytes) {
        if (startsWith(bytes, "ID3".getBytes(StandardCharsets.US_ASCII))
                || (has(bytes, 2) && (bytes[0] & 0xff) == 0xff && ((bytes[1] & 0xe0) == 0xe0))) {
            return "audio/mpeg";
        }
        if (startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII)) && asciiAt(bytes, 8, "WAVE")) {
            return "audio/wav";
        }
        if (startsWith(bytes, "OggS".getBytes(StandardCharsets.US_ASCII))) return "audio/ogg";
        if (startsWith(bytes, "fLaC".getBytes(StandardCharsets.US_ASCII))) return "audio/flac";
        if (startsWith(bytes, "#!AMR\n".getBytes(StandardCharsets.US_ASCII))) return "audio/amr";
        if (has(bytes, 2) && (bytes[0] & 0xff) == 0xff
                && (((bytes[1] & 0xf6) == 0xf0) || ((bytes[1] & 0xf6) == 0xf4))) return "audio/aac";
        if (has(bytes, 12) && asciiAt(bytes, 4, "ftyp")) return "audio/mp4";
        if (has(bytes, 4) && (bytes[0] & 0xff) == 0x1a && (bytes[1] & 0xff) == 0x45
                && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3) return "audio/webm";
        return "";
    }

    private static String detectImage(byte[] bytes) {
        if (has(bytes, 8) && (bytes[0] & 0xff) == 0x89 && asciiAt(bytes, 1, "PNG")) return "image/png";
        if (has(bytes, 3) && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) return "image/gif";
        if (has(bytes, 12) && asciiAt(bytes, 0, "RIFF") && asciiAt(bytes, 8, "WEBP")) return "image/webp";
        if (has(bytes, 12) && asciiAt(bytes, 4, "ftyp") && (asciiAt(bytes, 8, "avif") || asciiAt(bytes, 8, "avis"))) return "image/avif";
        return "";
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value == null || prefix == null || value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String expected) {
        if (bytes == null || expected == null || offset < 0 || bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) {
            if ((byte) expected.charAt(i) != bytes[offset + i]) return false;
        }
        return true;
    }

    private static boolean has(byte[] bytes, int length) {
        return bytes != null && bytes.length >= length;
    }

    private static String normalizeContentType(String value) {
        if (value == null) return "";
        int separator = value.indexOf(';');
        return (separator >= 0 ? value.substring(0, separator) : value).trim().toLowerCase(Locale.ROOT);
    }
}
