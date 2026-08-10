package com.example.sillyspringboot.illustration.service;

import com.example.sillyspringboot.illustration.entity.AppIllustrationAccessKey;
import com.example.sillyspringboot.illustration.mapper.AppIllustrationAccessKeyMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IllustrationAccessKeyService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppIllustrationAccessKeyMapper accessKeyMapper;

    public IllustrationAccessKeyService(AppIllustrationAccessKeyMapper accessKeyMapper) {
        this.accessKeyMapper = accessKeyMapper;
    }

    @Transactional(readOnly = true)
    public long countAdminKeys(String keyword, Boolean active) {
        return accessKeyMapper.countAdminList(trimToNull(keyword), active);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminKeys(String keyword, Boolean active, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return accessKeyMapper.listAdminPage(trimToNull(keyword), active, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> generateAdminKey(String createdBy, Integer ttlMinutes, Integer maxUses, String note) {
        int safeTtl = ttlMinutes == null ? 10 : Math.max(1, Math.min(1440, ttlMinutes));
        Integer safeMaxUses = maxUses == null || maxUses <= 0 ? null : Math.min(9999, maxUses);

        AppIllustrationAccessKey row = new AppIllustrationAccessKey();
        row.setAccessCode(newUniqueCode());
        row.setContentLevel(IllustrationWorkService.LEVEL_R18);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(safeTtl));
        row.setActive(Boolean.TRUE);
        row.setMaxUses(safeMaxUses);
        row.setUsedCount(0);
        row.setNote(limit(trimToNull(note), 255));
        row.setCreatedBy(limit(trimToNull(createdBy), 120));
        accessKeyMapper.insert(row);
        return toMap(accessKeyMapper.findById(row.getId()));
    }

    @Transactional
    public boolean disableAdminKey(long id) {
        return accessKeyMapper.disableById(id) > 0;
    }

    @Transactional
    public boolean removeAdminKey(long id) {
        return accessKeyMapper.hardDeleteById(id) > 0;
    }

    @Transactional(readOnly = true)
    public boolean canAccessR18(String accessCode) {
        return validate(accessCode).valid();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateForPublic(String accessCode) {
        Validation validation = validate(accessCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", validation.valid());
        data.put("contentLevel", validation.row() == null ? IllustrationWorkService.LEVEL_R18 : validation.row().getContentLevel());
        data.put("expiresAt", validation.row() == null ? null : validation.row().getExpiresAt());
        data.put("message", validation.message());
        return data;
    }

    private Validation validate(String accessCode) {
        String safeCode = normalizeCode(accessCode);
        if (safeCode == null) {
            return new Validation(false, null, "请输入临时密钥");
        }
        AppIllustrationAccessKey row = accessKeyMapper.findByCode(safeCode);
        if (row == null) {
            return new Validation(false, null, "密钥不存在");
        }
        if (!Boolean.TRUE.equals(row.getActive())) {
            return new Validation(false, row, "密钥已停用");
        }
        if (row.getExpiresAt() == null || !row.getExpiresAt().isAfter(LocalDateTime.now())) {
            return new Validation(false, row, "密钥已过期");
        }
        if (row.getMaxUses() != null && row.getUsedCount() != null && row.getUsedCount() >= row.getMaxUses()) {
            return new Validation(false, row, "密钥使用次数已用完");
        }
        if (!IllustrationWorkService.LEVEL_R18.equalsIgnoreCase(row.getContentLevel())) {
            return new Validation(false, row, "密钥分级不正确");
        }
        return new Validation(true, row, "密钥有效");
    }

    private String newUniqueCode() {
        for (int i = 0; i < 8; i++) {
            String code = randomCode();
            if (accessKeyMapper.findByCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "密钥生成失败，请重试");
    }

    private static String randomCode() {
        StringBuilder out = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            out.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return out.toString();
    }

    private Map<String, Object> toMap(AppIllustrationAccessKey row) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (row == null) {
            return data;
        }
        data.put("id", row.getId());
        data.put("accessCode", row.getAccessCode());
        data.put("contentLevel", row.getContentLevel());
        data.put("expiresAt", row.getExpiresAt());
        data.put("active", Boolean.TRUE.equals(row.getActive()));
        data.put("maxUses", row.getMaxUses());
        data.put("usedCount", row.getUsedCount() == null ? 0 : row.getUsedCount());
        data.put("note", row.getNote() == null ? "" : row.getNote());
        data.put("createdBy", row.getCreatedBy() == null ? "" : row.getCreatedBy());
        data.put("createdAt", row.getCreatedAt());
        data.put("updatedAt", row.getUpdatedAt());
        data.put("valid", validate(row.getAccessCode()).valid());
        return data;
    }

    private static String normalizeCode(String accessCode) {
        String value = trimToNull(accessCode);
        return value == null ? null : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String limit(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private record Validation(boolean valid, AppIllustrationAccessKey row, String message) {
    }
}
