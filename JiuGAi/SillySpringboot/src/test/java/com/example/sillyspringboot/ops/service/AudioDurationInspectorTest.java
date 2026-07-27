package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioDurationInspectorTest {

    @Test
    void readsActualWavDurationInsteadOfClientMetadata() {
        assertThat(AudioDurationInspector.durationMillis(wav(24), "audio/wav")).isEqualTo(24_000L);
    }

    @Test
    void rejectsAudioWhoseDurationCannotBeParsed() {
        assertThatThrownBy(() -> AudioDurationInspector.durationMillis(new byte[]{'R', 'I', 'F', 'F'}, "audio/wav"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法读取");
    }

    @Test
    void readsMp3FrameDuration() {
        assertThat(AudioDurationInspector.durationMillis(mp3(230), "audio/mpeg"))
                .isBetween(5_900L, 6_100L);
    }

    @Test
    void readsMp4MovieHeaderDuration() {
        assertThat(AudioDurationInspector.durationMillis(mp4(6_000), "audio/mp4"))
                .isEqualTo(6_000L);
    }

    @Test
    void readsOggOpusGranuleDuration() {
        assertThat(AudioDurationInspector.durationMillis(oggOpus(6), "audio/ogg"))
                .isEqualTo(6_000L);
    }

    @Test
    void readsFlacStreamInfoDuration() {
        assertThat(AudioDurationInspector.durationMillis(flac(16_000, 6), "audio/flac"))
                .isEqualTo(6_000L);
    }

    private static byte[] wav(int seconds) {
        int dataSize = 16_000 * 2 * seconds;
        byte[] bytes = new byte[44 + dataSize];
        ascii(bytes, 0, "RIFF");
        le32(bytes, 4, bytes.length - 8);
        ascii(bytes, 8, "WAVEfmt ");
        le32(bytes, 16, 16);
        bytes[20] = 1;
        bytes[22] = 1;
        le32(bytes, 24, 16_000);
        le32(bytes, 28, 32_000);
        bytes[32] = 2;
        bytes[34] = 16;
        ascii(bytes, 36, "data");
        le32(bytes, 40, dataSize);
        return bytes;
    }

    private static byte[] mp3(int frames) {
        int frameLength = 417;
        byte[] bytes = new byte[frameLength * frames];
        for (int frame = 0; frame < frames; frame++) {
            int offset = frame * frameLength;
            bytes[offset] = (byte) 0xff;
            bytes[offset + 1] = (byte) 0xfb;
            bytes[offset + 2] = (byte) 0x90;
            bytes[offset + 3] = 0;
        }
        return bytes;
    }

    private static byte[] mp4(int durationMs) {
        byte[] bytes = new byte[64];
        ascii(bytes, 4, "ftyp");
        ascii(bytes, 16, "mvhd");
        be32(bytes, 32, 1_000);
        be32(bytes, 36, durationMs);
        return bytes;
    }

    private static byte[] oggOpus(int seconds) {
        byte[] bytes = new byte[64];
        ascii(bytes, 0, "OggS");
        le64(bytes, 6, 48_000L * seconds);
        ascii(bytes, 30, "OpusHead");
        return bytes;
    }

    private static byte[] flac(int sampleRate, int seconds) {
        byte[] bytes = new byte[42];
        ascii(bytes, 0, "fLaC");
        bytes[4] = 0;
        bytes[7] = 34;
        int data = 8;
        bytes[data + 10] = (byte) (sampleRate >>> 12);
        bytes[data + 11] = (byte) (sampleRate >>> 4);
        bytes[data + 12] = (byte) ((sampleRate & 0x0f) << 4);
        be32(bytes, data + 14, sampleRate * seconds);
        return bytes;
    }

    private static void ascii(byte[] target, int offset, String value) {
        for (int i = 0; i < value.length(); i++) target[offset + i] = (byte) value.charAt(i);
    }

    private static void le32(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >>> 8);
        target[offset + 2] = (byte) (value >>> 16);
        target[offset + 3] = (byte) (value >>> 24);
    }

    private static void be32(byte[] target, int offset, long value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void le64(byte[] target, int offset, long value) {
        for (int i = 0; i < 8; i++) target[offset + i] = (byte) (value >>> (i * 8));
    }
}
