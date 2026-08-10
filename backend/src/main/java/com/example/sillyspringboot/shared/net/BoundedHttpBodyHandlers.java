package com.example.sillyspringboot.shared.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;

public final class BoundedHttpBodyHandlers {

    private BoundedHttpBodyHandlers() {
    }

    public static HttpResponse.BodyHandler<byte[]> ofByteArray(int maxBytes) {
        int limit = positiveLimit(maxBytes);
        return responseInfo -> {
            long announced = responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (announced > limit) {
                throw new BodyTooLargeException(limit);
            }
            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofInputStream(),
                    stream -> readBytes(stream, limit)
            );
        };
    }

    public static HttpResponse.BodyHandler<String> ofString(int maxBytes, Charset charset) {
        Charset safeCharset = charset == null ? Charset.defaultCharset() : charset;
        return responseInfo -> {
            long announced = responseInfo.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (announced > positiveLimit(maxBytes)) {
                throw new BodyTooLargeException(maxBytes);
            }
            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofInputStream(),
                    stream -> new String(readBytes(stream, maxBytes), safeCharset)
            );
        };
    }

    public static byte[] readBytes(InputStream stream, int maxBytes) {
        if (stream == null) {
            return new byte[0];
        }
        int limit = positiveLimit(maxBytes);
        try (InputStream input = stream) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new BodyTooLargeException(limit);
            }
            return bytes;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static int positiveLimit(int maxBytes) {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes must be between 1 and Integer.MAX_VALUE - 1");
        }
        return maxBytes;
    }

    public static final class BodyTooLargeException extends RuntimeException {
        public BodyTooLargeException(int limit) {
            super("upstream response exceeded " + limit + " bytes");
        }
    }
}
