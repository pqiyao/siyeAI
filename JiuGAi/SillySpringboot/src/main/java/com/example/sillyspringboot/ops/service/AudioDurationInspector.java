package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.nio.charset.StandardCharsets;

final class AudioDurationInspector {

    private AudioDurationInspector() {
    }

    static long durationMillis(byte[] audio, String mimeType) {
        long duration = switch (mimeType == null ? "" : mimeType) {
            case "audio/wav" -> wavDuration(audio);
            case "audio/mpeg" -> mp3Duration(audio);
            case "audio/mp4" -> mp4Duration(audio);
            case "audio/ogg" -> oggDuration(audio);
            case "audio/flac" -> flacDuration(audio);
            default -> -1L;
        };
        if (duration <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "无法读取参考音频时长，请使用 WAV、MP3、M4A、OGG 或 FLAC 文件"
            );
        }
        return duration;
    }

    private static long wavDuration(byte[] bytes) {
        if (!asciiAt(bytes, 0, "RIFF") || !asciiAt(bytes, 8, "WAVE")) return -1L;
        long byteRate = 0;
        long dataBytes = 0;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            long chunkSize = u32le(bytes, offset + 4);
            if (chunkSize < 0 || chunkSize > Integer.MAX_VALUE) return -1L;
            int dataOffset = offset + 8;
            if (asciiAt(bytes, offset, "fmt ") && chunkSize >= 12 && dataOffset + 12 <= bytes.length) {
                byteRate = u32le(bytes, dataOffset + 8);
            } else if (asciiAt(bytes, offset, "data")) {
                dataBytes = Math.min(chunkSize, Math.max(0, bytes.length - dataOffset));
            }
            long next = (long) dataOffset + chunkSize + (chunkSize & 1L);
            if (next <= offset || next > bytes.length) break;
            offset = (int) next;
        }
        return byteRate > 0 && dataBytes > 0 ? Math.round(dataBytes * 1000.0 / byteRate) : -1L;
    }

    private static long mp3Duration(byte[] bytes) {
        int offset = id3PayloadEnd(bytes);
        double milliseconds = 0;
        int frames = 0;
        while (offset + 4 <= bytes.length) {
            int header = u32beInt(bytes, offset);
            if ((header & 0xffe00000) != 0xffe00000) {
                offset++;
                continue;
            }
            int versionBits = (header >>> 19) & 0x3;
            int layerBits = (header >>> 17) & 0x3;
            int bitrateIndex = (header >>> 12) & 0xf;
            int sampleRateIndex = (header >>> 10) & 0x3;
            int padding = (header >>> 9) & 0x1;
            if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
                offset++;
                continue;
            }
            int version = versionBits == 3 ? 1 : (versionBits == 2 ? 2 : 25);
            int layer = 4 - layerBits;
            int bitrate = bitrateKbps(version, layer, bitrateIndex);
            int sampleRate = sampleRate(version, sampleRateIndex);
            if (bitrate <= 0 || sampleRate <= 0) {
                offset++;
                continue;
            }
            int samplesPerFrame = layer == 1 ? 384 : (layer == 3 && version != 1 ? 576 : 1152);
            int frameLength = layer == 1
                    ? ((12 * bitrate * 1000 / sampleRate) + padding) * 4
                    : (((layer == 3 && version != 1) ? 72 : 144) * bitrate * 1000 / sampleRate) + padding;
            if (frameLength <= 4 || offset + frameLength > bytes.length) break;
            milliseconds += samplesPerFrame * 1000.0 / sampleRate;
            frames++;
            offset += frameLength;
        }
        return frames > 0 ? Math.round(milliseconds) : -1L;
    }

    private static long mp4Duration(byte[] bytes) {
        int marker = indexOf(bytes, "mvhd".getBytes(StandardCharsets.US_ASCII), 0);
        if (marker < 4 || marker + 24 >= bytes.length) return -1L;
        int version = bytes[marker + 4] & 0xff;
        long timescale;
        long duration;
        if (version == 1) {
            if (marker + 36 > bytes.length) return -1L;
            timescale = u32be(bytes, marker + 24);
            duration = u64be(bytes, marker + 28);
        } else {
            timescale = u32be(bytes, marker + 16);
            duration = u32be(bytes, marker + 20);
        }
        return timescale > 0 && duration > 0 ? Math.round(duration * 1000.0 / timescale) : -1L;
    }

    private static long oggDuration(byte[] bytes) {
        int sampleRate = 0;
        int opus = indexOf(bytes, "OpusHead".getBytes(StandardCharsets.US_ASCII), 0);
        if (opus >= 0) sampleRate = 48_000;
        if (sampleRate == 0) {
            byte[] vorbis = new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'};
            int marker = indexOf(bytes, vorbis, 0);
            if (marker >= 0 && marker + 16 <= bytes.length) sampleRate = (int) u32le(bytes, marker + 12);
        }
        long granule = -1L;
        int offset = 0;
        while (true) {
            int page = indexOf(bytes, "OggS".getBytes(StandardCharsets.US_ASCII), offset);
            if (page < 0) break;
            if (page + 14 <= bytes.length) granule = u64le(bytes, page + 6);
            offset = page + 4;
        }
        return sampleRate > 0 && granule > 0 ? Math.round(granule * 1000.0 / sampleRate) : -1L;
    }

    private static long flacDuration(byte[] bytes) {
        if (!asciiAt(bytes, 0, "fLaC") || bytes.length < 42) return -1L;
        int offset = 4;
        while (offset + 4 <= bytes.length) {
            int type = bytes[offset] & 0x7f;
            int length = ((bytes[offset + 1] & 0xff) << 16)
                    | ((bytes[offset + 2] & 0xff) << 8)
                    | (bytes[offset + 3] & 0xff);
            int data = offset + 4;
            if (type == 0 && length >= 34 && data + 18 <= bytes.length) {
                long sampleRate = ((long) (bytes[data + 10] & 0xff) << 12)
                        | ((long) (bytes[data + 11] & 0xff) << 4)
                        | ((bytes[data + 12] & 0xf0) >>> 4);
                long totalSamples = ((long) (bytes[data + 13] & 0x0f) << 32)
                        | u32be(bytes, data + 14);
                return sampleRate > 0 && totalSamples > 0
                        ? Math.round(totalSamples * 1000.0 / sampleRate)
                        : -1L;
            }
            if (data + length <= offset || data + length > bytes.length) break;
            offset = data + length;
        }
        return -1L;
    }

    private static int id3PayloadEnd(byte[] bytes) {
        if (!asciiAt(bytes, 0, "ID3") || bytes.length < 10) return 0;
        int size = ((bytes[6] & 0x7f) << 21) | ((bytes[7] & 0x7f) << 14)
                | ((bytes[8] & 0x7f) << 7) | (bytes[9] & 0x7f);
        return Math.min(bytes.length, 10 + size);
    }

    private static int bitrateKbps(int version, int layer, int index) {
        int[][] mpeg1 = {
                {},
                {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
                {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
                {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}
        };
        int[][] mpeg2 = {
                {},
                {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
                {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
                {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}
        };
        return (version == 1 ? mpeg1 : mpeg2)[layer][index];
    }

    private static int sampleRate(int version, int index) {
        int base = new int[]{44_100, 48_000, 32_000}[index];
        return version == 1 ? base : (version == 2 ? base / 2 : base / 4);
    }

    private static int indexOf(byte[] value, byte[] needle, int start) {
        if (value == null || needle == null || needle.length == 0) return -1;
        for (int i = Math.max(0, start); i <= value.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (value[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static boolean asciiAt(byte[] bytes, int offset, String value) {
        if (bytes == null || value == null || offset < 0 || offset + value.length() > bytes.length) return false;
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    private static int u32beInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | (bytes[offset + 3] & 0xff);
    }

    private static long u32be(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 4 > bytes.length) return -1L;
        return Integer.toUnsignedLong(u32beInt(bytes, offset));
    }

    private static long u32le(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 4 > bytes.length) return -1L;
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static long u64be(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 8 > bytes.length) return -1L;
        long value = 0;
        for (int i = 0; i < 8; i++) value = (value << 8) | (bytes[offset + i] & 0xffL);
        return value;
    }

    private static long u64le(byte[] bytes, int offset) {
        if (bytes == null || offset < 0 || offset + 8 > bytes.length) return -1L;
        long value = 0;
        for (int i = 7; i >= 0; i--) value = (value << 8) | (bytes[offset + i] & 0xffL);
        return value;
    }
}
