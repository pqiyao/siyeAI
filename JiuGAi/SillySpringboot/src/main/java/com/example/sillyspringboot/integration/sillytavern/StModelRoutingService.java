package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.StModelProviderAdminDto;
import com.example.sillyspringboot.integration.sillytavern.dto.StModelRouteAdminDto;
import com.example.sillyspringboot.integration.sillytavern.entity.StModelProvider;
import com.example.sillyspringboot.integration.sillytavern.entity.StModelRoute;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelProviderMapper;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelRouteMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StModelRoutingService {

    public static final String DEFAULT_SCENE = "default_chat";

    private final StModelProviderMapper providerMapper;
    private final StModelRouteMapper routeMapper;
    private final SillyTavernProperties properties;

    public StModelRoutingService(
            StModelProviderMapper providerMapper,
            StModelRouteMapper routeMapper,
            SillyTavernProperties properties
    ) {
        this.providerMapper = providerMapper;
        this.routeMapper = routeMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AdminSnapshot getAdminSnapshot() {
        List<StModelProviderAdminDto> providers = providerMapper.listAll().stream().map(this::toProviderDto).toList();
        List<StModelRouteAdminDto> routes = routeMapper.listAll().stream().map(this::toRouteDto).toList();
        return new AdminSnapshot(
                providers,
                routes,
                DEFAULT_SCENE,
                new LegacySummary(
                        safe(properties.getChatCompletionSource()),
                        safe(properties.getDefaultModel()),
                        safe(properties.getReverseProxy()),
                        safe(properties.getPublicBaseUrl())
                )
        );
    }

    @Transactional
    public StModelProviderAdminDto saveProvider(StModelProviderAdminDto body) {
        StModelProvider normalized = normalizeProvider(body);
        StModelProvider existingByKey = providerMapper.findByProviderKey(normalized.getProviderKey());
        if (normalized.getId() == null) {
            if (existingByKey != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Provider Key 已存在");
            }
            providerMapper.insert(normalized);
            return toProviderDto(providerMapper.findById(normalized.getId()));
        }

        StModelProvider current = providerMapper.findById(normalized.getId());
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型供应商不存在");
        }
        if (existingByKey != null && !existingByKey.getId().equals(normalized.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Provider Key 已存在");
        }
        normalized.setConsecutiveFailures(current.getConsecutiveFailures());
        normalized.setCircuitOpenUntil(current.getCircuitOpenUntil());
        normalized.setLastError(current.getLastError());
        normalized.setLastUsedAt(current.getLastUsedAt());
        normalized.setLastHealthStatus(current.getLastHealthStatus());
        providerMapper.updateById(normalized);
        return toProviderDto(providerMapper.findById(normalized.getId()));
    }

    @Transactional
    public void deleteProvider(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少供应商 ID");
        }
        StModelProvider current = providerMapper.findById(id);
        if (current == null) {
            return;
        }
        if (routeMapper.countReferencingProvider(current.getProviderKey()) > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先移除引用该供应商的模型路由");
        }
        providerMapper.deleteById(id);
    }

    @Transactional
    public StModelRouteAdminDto saveRoute(StModelRouteAdminDto body) {
        StModelRoute normalized = normalizeRoute(body);
        StModelRoute existingByScene = routeMapper.findBySceneKey(normalized.getSceneKey());
        if (normalized.getId() == null) {
            if (existingByScene != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Scene Key 已存在");
            }
            routeMapper.insert(normalized);
            return toRouteDto(routeMapper.findById(normalized.getId()));
        }

        StModelRoute current = routeMapper.findById(normalized.getId());
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模型路由不存在");
        }
        if (existingByScene != null && !existingByScene.getId().equals(normalized.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Scene Key 已存在");
        }
        routeMapper.updateById(normalized);
        return toRouteDto(routeMapper.findById(normalized.getId()));
    }

    @Transactional
    public void deleteRoute(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少路由 ID");
        }
        routeMapper.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ResolvedRoute resolveForScene(String rawSceneKey) {
        String sceneKey = firstNonBlank(rawSceneKey, DEFAULT_SCENE);
        StModelRoute route = routeMapper.findBySceneKey(sceneKey);
        if (route == null || !Boolean.TRUE.equals(route.getEnabled())) {
            return new ResolvedRoute(sceneKey, "", List.of());
        }

        Map<String, StModelProvider> providerMap = new LinkedHashMap<>();
        for (StModelProvider provider : providerMapper.listEnabled()) {
            providerMap.put(provider.getProviderKey(), provider);
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(firstNonBlank(route.getPrimaryProviderKey()));
        keys.addAll(splitKeys(route.getFallbackProviderKeys()));

        LocalDateTime now = LocalDateTime.now();
        List<ResolvedProvider> active = new ArrayList<>();
        List<ResolvedProvider> disabledByCircuit = new ArrayList<>();
        for (String key : keys) {
            StModelProvider provider = providerMap.get(key);
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            ResolvedProvider resolved = toResolvedProvider(provider);
            if (provider.getCircuitOpenUntil() != null && provider.getCircuitOpenUntil().isAfter(now)) {
                disabledByCircuit.add(resolved);
            } else {
                active.add(resolved);
            }
        }
        if (active.isEmpty() && !disabledByCircuit.isEmpty()) {
            active.add(disabledByCircuit.get(0));
        }
        return new ResolvedRoute(sceneKey, firstNonBlank(route.getDisplayName(), route.getSceneKey()), List.copyOf(active));
    }

    @Transactional
    public void recordSuccess(String providerKey) {
        String safeKey = firstNonBlank(providerKey);
        if (!safeKey.isBlank()) {
            providerMapper.markSuccess(safeKey);
        }
    }

    @Transactional
    public void recordFailure(String providerKey, String errorMessage) {
        String safeKey = firstNonBlank(providerKey);
        if (safeKey.isBlank()) {
            return;
        }
        StModelProvider provider = providerMapper.findByProviderKey(safeKey);
        if (provider == null) {
            return;
        }
        providerMapper.markFailure(
                safeKey,
                trim(errorMessage, 500),
                Math.max(1, nvl(provider.getFailureThreshold(), 3)),
                Math.max(30, nvl(provider.getCooldownSeconds(), 180))
        );
    }

    private StModelProvider normalizeProvider(StModelProviderAdminDto body) {
        StModelProvider row = new StModelProvider();
        row.setId(body == null ? null : body.getId());
        row.setProviderKey(normalizeKey(body == null ? null : body.getProviderKey(), "providerKey"));
        row.setDisplayName(requireText(body == null ? null : body.getDisplayName(), "displayName"));
        row.setStSource(normalizeSource(body == null ? null : body.getStSource()));
        row.setModelName(requireText(body == null ? null : body.getModelName(), "modelName"));
        row.setReverseProxy(safe(body == null ? null : body.getReverseProxy()));
        row.setProxyPassword(safe(body == null ? null : body.getProxyPassword()));
        row.setCustomUrl(safe(body == null ? null : body.getCustomUrl()));
        row.setPriority(Math.max(1, Math.min(9999, nvl(body == null ? null : body.getPriority(), 100))));
        row.setEnabled(body == null || body.getEnabled() == null || body.getEnabled());
        row.setFailureThreshold(Math.max(1, Math.min(20, nvl(body == null ? null : body.getFailureThreshold(), 3))));
        row.setCooldownSeconds(Math.max(30, Math.min(3600, nvl(body == null ? null : body.getCooldownSeconds(), 180))));
        row.setConsecutiveFailures(nvl(body == null ? null : body.getConsecutiveFailures(), 0));
        row.setCircuitOpenUntil(body == null ? null : body.getCircuitOpenUntil());
        row.setLastError(safe(body == null ? null : body.getLastError()));
        row.setLastUsedAt(body == null ? null : body.getLastUsedAt());
        row.setLastHealthStatus(firstNonBlank(body == null ? null : body.getLastHealthStatus(), "unknown"));
        row.setNote(trim(body == null ? null : body.getNote(), 255));
        return row;
    }

    private StModelRoute normalizeRoute(StModelRouteAdminDto body) {
        StModelRoute row = new StModelRoute();
        row.setId(body == null ? null : body.getId());
        row.setSceneKey(normalizeKey(body == null ? null : body.getSceneKey(), "sceneKey"));
        row.setDisplayName(requireText(body == null ? null : body.getDisplayName(), "displayName"));

        String primaryKey = normalizeKey(body == null ? null : body.getPrimaryProviderKey(), "primaryProviderKey");
        if (providerMapper.findByProviderKey(primaryKey) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "主供应商不存在");
        }

        List<String> fallbackKeys = splitKeys(body == null ? null : body.getFallbackProviderKeys());
        for (String key : fallbackKeys) {
            if (key.equals(primaryKey)) {
                continue;
            }
            if (providerMapper.findByProviderKey(key) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Fallback 供应商不存在: " + key);
            }
        }

        row.setPrimaryProviderKey(primaryKey);
        row.setFallbackProviderKeys(String.join("|", fallbackKeys));
        row.setEnabled(body == null || body.getEnabled() == null || body.getEnabled());
        row.setNote(trim(body == null ? null : body.getNote(), 255));
        return row;
    }

    private StModelProviderAdminDto toProviderDto(StModelProvider row) {
        StModelProviderAdminDto dto = new StModelProviderAdminDto();
        if (row == null) {
            return dto;
        }
        dto.setId(row.getId());
        dto.setProviderKey(row.getProviderKey());
        dto.setDisplayName(row.getDisplayName());
        dto.setStSource(row.getStSource());
        dto.setModelName(row.getModelName());
        dto.setReverseProxy(row.getReverseProxy());
        dto.setProxyPassword(row.getProxyPassword());
        dto.setCustomUrl(row.getCustomUrl());
        dto.setPriority(row.getPriority());
        dto.setEnabled(row.getEnabled());
        dto.setFailureThreshold(row.getFailureThreshold());
        dto.setCooldownSeconds(row.getCooldownSeconds());
        dto.setConsecutiveFailures(row.getConsecutiveFailures());
        dto.setCircuitOpenUntil(row.getCircuitOpenUntil());
        dto.setLastError(row.getLastError());
        dto.setLastUsedAt(row.getLastUsedAt());
        dto.setLastHealthStatus(row.getLastHealthStatus());
        dto.setNote(row.getNote());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    private StModelRouteAdminDto toRouteDto(StModelRoute row) {
        StModelRouteAdminDto dto = new StModelRouteAdminDto();
        if (row == null) {
            return dto;
        }
        dto.setId(row.getId());
        dto.setSceneKey(row.getSceneKey());
        dto.setDisplayName(row.getDisplayName());
        dto.setPrimaryProviderKey(row.getPrimaryProviderKey());
        dto.setFallbackProviderKeys(row.getFallbackProviderKeys());
        dto.setEnabled(row.getEnabled());
        dto.setNote(row.getNote());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }

    private ResolvedProvider toResolvedProvider(StModelProvider row) {
        return new ResolvedProvider(
                row.getProviderKey(),
                firstNonBlank(row.getDisplayName(), row.getProviderKey()),
                row.getStSource(),
                row.getModelName(),
                row.getReverseProxy(),
                row.getProxyPassword(),
                row.getCustomUrl()
        );
    }

    private static List<String> splitKeys(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        LinkedHashSet<String> values = Arrays.stream(raw.split("[|,]"))
                .map(item -> normalizeKey(item, null))
                .filter(item -> !item.isBlank())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return List.copyOf(values);
    }

    private static String normalizeSource(String raw) {
        String value = firstNonBlank(raw).toLowerCase(Locale.ROOT);
        List<String> allowed = List.of(
                "openrouter", "openai", "custom", "claude", "deepseek", "groq",
                "xai", "mistralai", "cohere", "perplexity", "vertexai",
                "makersuite", "fireworks", "moonshot", "siliconflow", "azure_openai"
        );
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的 ST Source: " + value);
        }
        return value;
    }

    private static String normalizeKey(String raw, String fieldName) {
        String value = firstNonBlank(raw).toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (value.isBlank()) {
            if (fieldName != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldName + " 不能为空");
            }
            return "";
        }
        if (!value.matches("[a-z0-9_./]+")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    fieldName == null ? "Key 格式不合法" : fieldName + " 格式不合法"
            );
        }
        return value;
    }

    private static String requireText(String raw, String fieldName) {
        String value = firstNonBlank(raw);
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldName + " 不能为空");
        }
        return trim(value, 255);
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                return part.trim();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int nvl(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static String trim(String value, int max) {
        String text = safe(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max).trim();
    }

    public record ResolvedProvider(
            String providerKey,
            String displayName,
            String stSource,
            String modelName,
            String reverseProxy,
            String proxyPassword,
            String customUrl
    ) {}

    public record ResolvedRoute(
            String sceneKey,
            String displayName,
            List<ResolvedProvider> providers
    ) {}

    public record LegacySummary(
            String source,
            String defaultModel,
            String reverseProxy,
            String publicBaseUrl
    ) {}

    public record AdminSnapshot(
            List<StModelProviderAdminDto> providers,
            List<StModelRouteAdminDto> routes,
            String defaultSceneKey,
            LegacySummary legacy
    ) {}
}
