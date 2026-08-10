package com.example.sillyspringboot.ops.generation.service;

import com.example.sillyspringboot.ops.generation.dto.GenerationModelPricingAdminDto;
import com.example.sillyspringboot.ops.generation.entity.GenerationModelPricing;
import com.example.sillyspringboot.ops.generation.mapper.GenerationModelPricingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class GenerationModelPricingService {

    private static final BigDecimal MAX_PRICE_PER_MILLION = new BigDecimal("1000000");

    private final GenerationModelPricingMapper mapper;

    public GenerationModelPricingService(GenerationModelPricingMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<GenerationModelPricingAdminDto> listAll() {
        return mapper.listAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public GenerationModelPricingAdminDto save(GenerationModelPricingAdminDto body) {
        GenerationModelPricing row = normalize(body);
        if (row.getId() == null) {
            GenerationModelPricing sameVersion = mapper.findByIdentity(row);
            if (sameVersion != null) {
                row.setId(sameVersion.getId());
            }
        } else if (mapper.findById(row.getId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型价格不存在");
        }

        if (row.getId() == null) {
            mapper.insert(row);
        } else {
            mapper.updateById(row);
        }
        return toDto(mapper.findById(row.getId()));
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少价格 ID");
        }
        mapper.deleteById(id);
    }

    @Transactional(readOnly = true)
    public GenerationModelPricing resolve(String providerKey, String model, LocalDateTime effectiveAt) {
        String safeProvider = normalizeProviderKey(providerKey);
        String safeModel = safe(model);
        if (safeModel.isBlank()) {
            return null;
        }
        LocalDateTime at = effectiveAt == null ? LocalDateTime.now() : effectiveAt;
        Comparator<GenerationModelPricing> order = Comparator
                .comparingInt((GenerationModelPricing row) -> safeProvider.equals(row.getProviderKey()) ? 0 : 1)
                .thenComparingInt(row -> safeModel.equalsIgnoreCase(row.getModelPattern()) ? 0 : 1)
                .thenComparing((GenerationModelPricing row) -> literalLength(row.getModelPattern()), Comparator.reverseOrder())
                .thenComparing(GenerationModelPricing::getEffectiveFrom, Comparator.reverseOrder())
                .thenComparing(GenerationModelPricing::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        return mapper.listEffective(safeProvider, at).stream()
                .filter(row -> globMatches(row.getModelPattern(), safeModel))
                .min(order)
                .orElse(null);
    }

    private GenerationModelPricing normalize(GenerationModelPricingAdminDto body) {
        if (body == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "价格配置不能为空");
        }
        GenerationModelPricing row = new GenerationModelPricing();
        row.setId(body.getId());
        row.setProviderKey(normalizeProviderKey(body.getProviderKey()));
        row.setModelPattern(requireText(body.getModelPattern(), "modelPattern", 255));
        row.setVersion(defaultText(body.getVersion(), "v1", 64));
        row.setCurrency(defaultText(body.getCurrency(), "USD", 3).toUpperCase(Locale.ROOT));
        if (!"USD".equals(row.getCurrency())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前成本汇总仅支持 USD");
        }
        row.setInputUsdPerMillionTokens(validPrice(body.getInputUsdPerMillionTokens(), "inputUsdPerMillionTokens"));
        row.setOutputUsdPerMillionTokens(validPrice(body.getOutputUsdPerMillionTokens(), "outputUsdPerMillionTokens"));
        row.setEffectiveFrom(body.getEffectiveFrom() == null ? LocalDateTime.now() : body.getEffectiveFrom());
        row.setEffectiveTo(body.getEffectiveTo());
        if (row.getEffectiveTo() != null && !row.getEffectiveTo().isAfter(row.getEffectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "effectiveTo 必须晚于 effectiveFrom");
        }
        row.setEnabled(body.getEnabled() == null || body.getEnabled());
        row.setNote(trim(safe(body.getNote()), 255));
        return row;
    }

    private static String normalizeProviderKey(String raw) {
        String value = safe(raw).toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "providerKey 不能为空");
        }
        if (!value.matches("[a-z0-9_./*]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "providerKey 格式不合法");
        }
        return trim(value, 80);
    }

    private static BigDecimal validPrice(BigDecimal value, String field) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.signum() < 0 || normalized.compareTo(MAX_PRICE_PER_MILLION) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 超出允许范围");
        }
        return normalized;
    }

    static boolean globMatches(String rawPattern, String value) {
        String pattern = safe(rawPattern);
        if (pattern.isBlank()) {
            return false;
        }
        String[] parts = pattern.split("\\*", -1);
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        regex.append('$');
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(safe(value))
                .matches();
    }

    private static int literalLength(String pattern) {
        return safe(pattern).replace("*", "").length();
    }

    private GenerationModelPricingAdminDto toDto(GenerationModelPricing row) {
        GenerationModelPricingAdminDto dto = new GenerationModelPricingAdminDto();
        if (row == null) {
            return dto;
        }
        dto.setId(row.getId());
        dto.setProviderKey(row.getProviderKey());
        dto.setModelPattern(row.getModelPattern());
        dto.setVersion(row.getVersion());
        dto.setCurrency(row.getCurrency());
        dto.setInputUsdPerMillionTokens(row.getInputUsdPerMillionTokens());
        dto.setOutputUsdPerMillionTokens(row.getOutputUsdPerMillionTokens());
        dto.setEffectiveFrom(row.getEffectiveFrom());
        dto.setEffectiveTo(row.getEffectiveTo());
        dto.setEnabled(row.getEnabled());
        dto.setNote(row.getNote());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    private static String requireText(String value, String field, int max) {
        String normalized = safe(value);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 不能为空");
        }
        if (normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, field + " 不能包含换行");
        }
        return trim(normalized, max);
    }

    private static String defaultText(String value, String fallback, int max) {
        return trim(StringUtils.hasText(value) ? value.trim() : fallback, max);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
