package com.example.sillyspringboot.admin.security;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminCaptchaService {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 5;
    private static final int MAX_CHALLENGES = 10_000;

    private final RuoYiAdminProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, CaptchaEntry> challenges = new ConcurrentHashMap<>();

    public AdminCaptchaService(RuoYiAdminProperties properties) {
        this.properties = properties;
    }

    public CaptchaChallenge createChallenge() {
        return createChallenge(randomCode());
    }

    CaptchaChallenge createChallenge(String answer) {
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (challenges.size() >= MAX_CHALLENGES) {
            challenges.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                    .ifPresent(entry -> challenges.remove(entry.getKey(), entry.getValue()));
        }
        String normalized = normalize(answer);
        String uuid = UUID.randomUUID().toString();
        long ttlMillis = Math.max(30, properties.getCaptchaTtlSeconds()) * 1000L;
        challenges.put(uuid, new CaptchaEntry(normalized, now + ttlMillis));
        return new CaptchaChallenge(uuid, renderGif(normalized));
    }

    public boolean verifyAndConsume(String uuid, String answer) {
        if (uuid == null || uuid.isBlank() || answer == null || answer.isBlank()) {
            return false;
        }
        CaptchaEntry entry = challenges.remove(uuid.trim());
        if (entry == null || entry.expiresAtMillis() < System.currentTimeMillis()) {
            return false;
        }
        byte[] expected = entry.answer().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] supplied = normalize(answer).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    private void evictExpired(long now) {
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private String randomCode() {
        StringBuilder value = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            value.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }

    private String renderGif(String code) {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(246, 248, 252));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());

            for (int i = 0; i < 8; i++) {
                graphics.setColor(new Color(130 + random.nextInt(100), 130 + random.nextInt(100), 130 + random.nextInt(100)));
                graphics.drawLine(
                        random.nextInt(image.getWidth()),
                        random.nextInt(image.getHeight()),
                        random.nextInt(image.getWidth()),
                        random.nextInt(image.getHeight())
                );
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(25 + random.nextInt(90), 35 + random.nextInt(90), 55 + random.nextInt(90)));
                graphics.drawString(String.valueOf(code.charAt(i)), 8 + i * 22, 29 + random.nextInt(5));
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "gif", output)) {
                throw new IllegalStateException("GIF image writer is unavailable");
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render admin captcha", ex);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record CaptchaEntry(String answer, long expiresAtMillis) {
    }

    public record CaptchaChallenge(String uuid, String imageBase64) {
    }
}
