package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.entity.AiProviderAccount;
import com.example.sillyspringboot.ai.entity.AiProviderDeployment;
import com.example.sillyspringboot.ai.entity.AiResolvedDeployment;
import com.example.sillyspringboot.ai.entity.AiRoute;
import com.example.sillyspringboot.ai.entity.AiRouteMember;
import com.example.sillyspringboot.ai.mapper.AiRoutingMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.model.AiProtocol;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.integration.sillytavern.entity.StModelProvider;
import com.example.sillyspringboot.integration.sillytavern.entity.StModelRoute;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelProviderMapper;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelRouteMapper;
import com.example.sillyspringboot.shared.crypto.SensitiveTextCrypto;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AiRoutingService {

    private static final Logger log = LoggerFactory.getLogger(AiRoutingService.class);

    public record ResolvedProvider(
            long deploymentId,
            String providerKey,
            String displayName,
            String vendor,
            String baseUrl,
            String apiKey,
            AiCapability capability,
            AiProtocol protocol,
            String modelName,
            String voiceName,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds,
            int attemptOrder
    ) {
        public ResolvedProvider(
                long deploymentId, String providerKey, String displayName, String vendor,
                String baseUrl, String apiKey, AiCapability capability, AiProtocol protocol,
                String modelName, String voiceName, int attemptOrder
        ) {
            this(deploymentId, providerKey, displayName, vendor, baseUrl, apiKey, capability, protocol,
                    modelName, voiceName, 10, 90, attemptOrder);
        }
    }

    public record DraftCredential(
            Long accountId,
            Long deploymentId,
            String vendor,
            String baseUrl,
            String apiKey,
            AiCapability capability,
            String modelName,
            String voiceName,
            int connectTimeoutSeconds,
            int requestTimeoutSeconds
    ) {}

    private final AiRoutingMapper mapper;
    private final AiProviderCatalogService catalogService;
    private final SensitiveTextCrypto crypto;
    private final AiRoutingRuntimeSettingsService runtimeSettingsService;
    private final StModelProviderMapper legacyProviderMapper;
    private final StModelRouteMapper legacyRouteMapper;

    public AiRoutingService(
            AiRoutingMapper mapper,
            AiProviderCatalogService catalogService,
            SensitiveTextCrypto crypto,
            AiRoutingRuntimeSettingsService runtimeSettingsService,
            StModelProviderMapper legacyProviderMapper,
            StModelRouteMapper legacyRouteMapper
    ) {
        this.mapper = mapper;
        this.catalogService = catalogService;
        this.crypto = crypto;
        this.runtimeSettingsService = runtimeSettingsService;
        this.legacyProviderMapper = legacyProviderMapper;
        this.legacyRouteMapper = legacyRouteMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSnapshot() {
        List<Map<String, Object>> accounts = mapper.listAccounts().stream().map(this::accountView).toList();
        List<Map<String, Object>> deployments = mapper.listDeployments().stream()
                .map(AiRoutingService::deploymentView)
                .toList();
        List<Map<String, Object>> routes = mapper.listRoutes().stream().map(route -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", route.getId());
            item.put("routeKey", route.getRouteKey());
            item.put("displayName", route.getDisplayName());
            item.put("capability", route.getCapability());
            item.put("enabled", route.getEnabled());
            item.put("note", safe(route.getNote()));
            item.put("deploymentIds", mapper.listRouteMembers(route.getId()).stream()
                    .map(AiRouteMember::getDeploymentId).toList());
            return item;
        }).toList();
        Map<String, Object> flags = runtimeSettingsService.toMap(runtimeSettingsService.current());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accounts", accounts);
        result.put("deployments", deployments);
        result.put("routes", routes);
        result.put("catalog", catalogService.publicCatalog());
        result.put("flags", flags);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> capabilitySummary(AiCapability capability) {
        if (capability == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "AI 能力不能为空");
        }
        String routeKey = capability.defaultRouteKey();
        AiRoute route = mapper.findRouteByKey(routeKey);
        boolean routeDefined = route != null;
        boolean routeEnabled = routeDefined && Boolean.TRUE.equals(route.getEnabled());
        List<AiRouteMember> members = routeDefined ? mapper.listRouteMembers(route.getId()) : List.of();
        List<Map<String, Object>> nodes = new ArrayList<>();
        int configuredNodeCount = 0;
        int availableNodeCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (AiRouteMember member : members) {
            AiProviderDeployment deployment = mapper.findDeploymentById(member.getDeploymentId());
            if (deployment == null || !capability.name().equalsIgnoreCase(deployment.getCapability())) {
                continue;
            }
            AiProviderAccount account = mapper.findAccountById(deployment.getAccountId());
            boolean credentialConfigured = account != null
                    && StringUtils.hasText(decryptQuietly(account.getApiKeyCipher()));
            boolean nodeConfigured = account != null
                    && Boolean.TRUE.equals(account.getEnabled())
                    && Boolean.TRUE.equals(deployment.getEnabled())
                    && credentialConfigured;
            boolean circuitOpen = deployment.getCircuitOpenUntil() != null
                    && deployment.getCircuitOpenUntil().isAfter(now);
            if (nodeConfigured) {
                configuredNodeCount++;
                if (!circuitOpen) {
                    availableNodeCount++;
                }
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("deploymentId", deployment.getId());
            node.put("attemptOrder", member.getSortOrder());
            node.put("providerKey", account == null ? "" : safe(account.getProviderKey()));
            node.put("displayName", account == null ? "已删除供应商" : safe(account.getDisplayName()));
            node.put("vendor", account == null ? "" : safe(account.getVendor()));
            node.put("modelName", safe(deployment.getModelName()));
            node.put("voiceName", safe(deployment.getVoiceName()));
            node.put("enabled", nodeConfigured);
            node.put("credentialConfigured", credentialConfigured);
            node.put("healthStatus", circuitOpen ? "circuit_open" : safe(deployment.getLastHealthStatus()));
            nodes.add(node);
        }

        boolean runtimeEnabled = isCapabilityEnabled(capability);
        boolean routeConfigured = routeEnabled && configuredNodeCount > 0;
        String status;
        if (!runtimeEnabled) {
            status = "runtime_disabled";
        } else if (!routeDefined) {
            status = "route_missing";
        } else if (!routeEnabled) {
            status = "route_disabled";
        } else if (configuredNodeCount == 0) {
            status = "no_configured_nodes";
        } else if (availableNodeCount == 0) {
            status = "temporarily_unavailable";
        } else {
            status = "ready";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capability.name());
        result.put("routeKey", routeKey);
        result.put("routeName", routeDefined ? safe(route.getDisplayName()) : "");
        result.put("runtimeEnabled", runtimeEnabled);
        result.put("routeDefined", routeDefined);
        result.put("routeEnabled", routeEnabled);
        result.put("routeConfigured", routeConfigured);
        result.put("ready", runtimeEnabled && routeConfigured && availableNodeCount > 0);
        result.put("status", status);
        result.put("deploymentCount", members.size());
        result.put("configuredNodeCount", configuredNodeCount);
        result.put("availableNodeCount", availableNodeCount);
        result.put("nodes", nodes);
        return result;
    }

    @Transactional
    public Map<String, Object> saveProvider(Map<String, Object> body) {
        Long accountId = longValue(body == null ? null : body.get("accountId"));
        Long deploymentId = longValue(body == null ? null : body.get("deploymentId"));
        AiProviderAccount current = accountId == null ? null : mapper.findAccountById(accountId);
        if (accountId != null && current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商账户不存在");
        }

        String vendor = safe(body == null ? null : body.get("vendor")).toLowerCase(Locale.ROOT);
        AiProviderCatalogService.ProviderDefinition definition = catalogService.require(vendor);
        AiCapability capability = parseCapability(body == null ? null : body.get("capability"));
        if (!definition.capabilities().contains(capability)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该供应商不支持当前能力");
        }
        String providerKey = normalizeKey(body == null ? null : body.get("providerKey"));
        String displayName = required(body == null ? null : body.get("displayName"), "显示名称");
        String baseUrl = catalogService.normalizeBaseUrl(vendor, safe(body == null ? null : body.get("baseUrl")));
        String apiKeyAction = safe(body == null ? null : body.get("apiKeyAction")).toLowerCase(Locale.ROOT);
        String submittedKey = safe(body == null ? null : body.get("apiKey"));
        String apiKeyCipher = current == null ? "" : safe(current.getApiKeyCipher());
        if ("clear".equals(apiKeyAction)) {
            apiKeyCipher = "";
        } else if (StringUtils.hasText(submittedKey)) {
            apiKeyCipher = crypto.encrypt(submittedKey);
        }
        if (!StringUtils.hasText(apiKeyCipher) && bool(body == null ? null : body.get("accountEnabled"), true)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API Key 不能为空");
        }

        AiProviderAccount duplicate = mapper.findAccountByKey(providerKey);
        if (duplicate != null && (accountId == null || !duplicate.getId().equals(accountId))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Provider Key 已存在");
        }
        boolean accountEnabled = bool(body == null ? null : body.get("accountEnabled"), true);
        int connectTimeoutSeconds = intValue(body == null ? null : body.get("connectTimeoutSeconds"), 10, 1, 60);
        int requestTimeoutSeconds = intValue(body == null ? null : body.get("requestTimeoutSeconds"), 90, 5, 600);
        String note = trim(body == null ? null : body.get("note"), 255);
        boolean connectionChanged = current != null && (
                !Objects.equals(current.getVendor(), vendor)
                        || !Objects.equals(current.getBaseUrl(), baseUrl)
                        || !Objects.equals(current.getApiKeyCipher(), apiKeyCipher)
                        || !Objects.equals(current.getEnabled(), accountEnabled)
                        || !Objects.equals(current.getConnectTimeoutSeconds(), connectTimeoutSeconds)
                        || !Objects.equals(current.getRequestTimeoutSeconds(), requestTimeoutSeconds)
        );
        boolean accountChanged = current != null && (
                connectionChanged
                        || !Objects.equals(current.getProviderKey(), providerKey)
                        || !Objects.equals(current.getDisplayName(), displayName)
                        || !Objects.equals(safe(current.getNote()), note)
        );
        if (connectionChanged && mapper.countDeploymentsForAccount(accountId) > 1
                && !bool(body == null ? null : body.get("confirmSharedAccountChange"), false)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "该账户被多个能力共用，修改地址、Key、启用状态或超时会同时影响它们，请确认影响范围后重试");
        }

        AiProviderAccount account = current == null ? new AiProviderAccount() : current;
        account.setProviderKey(providerKey);
        account.setDisplayName(displayName);
        account.setVendor(vendor);
        account.setBaseUrl(baseUrl);
        account.setApiKeyCipher(apiKeyCipher);
        account.setEnabled(accountEnabled);
        account.setConnectTimeoutSeconds(connectTimeoutSeconds);
        account.setRequestTimeoutSeconds(requestTimeoutSeconds);
        account.setNote(note);
        if (account.getId() == null) {
            mapper.insertAccount(account);
        } else if (accountChanged) {
            Long submittedVersion = longValue(body == null ? null : body.get("accountVersion"));
            if (submittedVersion == null || !submittedVersion.equals(current.getVersionNo())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "供应商账户已被其他管理员修改，请刷新页面后重试");
            }
            account.setVersionNo(submittedVersion);
            if (mapper.updateAccount(account) != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "供应商账户已被其他管理员修改，请刷新页面后重试");
            }
            if (connectionChanged) {
                mapper.resetDeploymentHealthByAccountId(account.getId());
            }
        }

        AiProviderDeployment deployment = deploymentId == null ? null : mapper.findDeploymentById(deploymentId);
        if (deploymentId != null && deployment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "能力模型不存在");
        }
        if (deployment == null) {
            deployment = new AiProviderDeployment();
        } else if (!Objects.equals(deployment.getAccountId(), account.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "能力模型与供应商账户不匹配，请刷新页面后重试");
        }
        String protocolType = parseProtocol(body == null ? null : body.get("protocolType"), capability).name();
        String modelName = required(body == null ? null : body.get("modelName"), "模型 ID");
        String voiceName = trim(body == null ? null : body.get("voiceName"), 255);
        boolean deploymentEnabled = bool(body == null ? null : body.get("deploymentEnabled"), true);
        int failureThreshold = intValue(body == null ? null : body.get("failureThreshold"), 3, 1, 20);
        int cooldownSeconds = intValue(body == null ? null : body.get("cooldownSeconds"), 180, 30, 3600);
        boolean deploymentChanged = deployment.getId() != null && (
                !Objects.equals(deployment.getCapability(), capability.name())
                        || !Objects.equals(deployment.getProtocolType(), protocolType)
                        || !Objects.equals(deployment.getModelName(), modelName)
                        || !Objects.equals(safe(deployment.getVoiceName()), voiceName)
                        || !Objects.equals(deployment.getEnabled(), deploymentEnabled)
                        || !Objects.equals(deployment.getFailureThreshold(), failureThreshold)
                        || !Objects.equals(deployment.getCooldownSeconds(), cooldownSeconds)
        );
        if (mapper.countDuplicateDeployment(account.getId(), capability.name(), modelName, voiceName, deployment.getId()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该账户下已存在相同能力、模型和音色配置");
        }
        deployment.setAccountId(account.getId());
        deployment.setCapability(capability.name());
        deployment.setProtocolType(protocolType);
        deployment.setModelName(modelName);
        deployment.setVoiceName(voiceName);
        deployment.setEnabled(deploymentEnabled);
        deployment.setFailureThreshold(failureThreshold);
        deployment.setCooldownSeconds(cooldownSeconds);
        try {
            if (deployment.getId() == null) {
                mapper.insertDeployment(deployment);
            } else {
                mapper.updateDeployment(deployment);
                if (deploymentChanged && !connectionChanged) {
                    mapper.resetDeploymentHealth(deployment.getId());
                }
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该账户下已存在相同能力、模型和音色配置");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("account", accountView(mapper.findAccountById(account.getId())));
        result.put("deployment", deploymentView(mapper.findDeploymentById(deployment.getId())));
        return result;
    }

    @Transactional
    public Map<String, Object> saveRoute(Map<String, Object> body) {
        Long id = longValue(body == null ? null : body.get("id"));
        AiRoute route = id == null ? null : mapper.findRouteById(id);
        if (id != null && route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "能力路由不存在");
        }
        if (route == null) {
            route = new AiRoute();
        }
        AiCapability capability = parseCapability(body == null ? null : body.get("capability"));
        String routeKey = safe(body == null ? null : body.get("routeKey")).toLowerCase(Locale.ROOT);
        if (routeKey.isBlank()) {
            routeKey = capability.defaultRouteKey();
        }
        if (!routeKey.matches("[a-z0-9_.-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Route Key 格式不合法");
        }
        AiRoute duplicate = mapper.findRouteByKey(routeKey);
        if (duplicate != null && (id == null || !duplicate.getId().equals(id))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Route Key 已存在");
        }
        List<Long> deploymentIds = longList(body == null ? null : body.get("deploymentIds"));
        if (deploymentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路由至少需要一个能力模型");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(deploymentIds);
        for (Long deploymentId : uniqueIds) {
            AiProviderDeployment deployment = mapper.findDeploymentById(deploymentId);
            if (deployment == null || !capability.name().equalsIgnoreCase(deployment.getCapability())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "路由包含不存在或能力不匹配的模型");
            }
        }
        route.setRouteKey(routeKey);
        route.setDisplayName(required(body == null ? null : body.get("displayName"), "路由名称"));
        route.setCapability(capability.name());
        route.setEnabled(bool(body == null ? null : body.get("enabled"), true));
        route.setNote(trim(body == null ? null : body.get("note"), 255));
        if (route.getId() == null) {
            mapper.insertRoute(route);
        } else {
            mapper.updateRoute(route);
            mapper.deleteRouteMembers(route.getId());
        }
        int order = 1;
        for (Long deploymentId : uniqueIds) {
            AiRouteMember member = new AiRouteMember();
            member.setRouteId(route.getId());
            member.setDeploymentId(deploymentId);
            member.setSortOrder(order++);
            mapper.insertRouteMember(member);
        }
        return adminSnapshot();
    }

    @Transactional
    public Map<String, Object> importLegacyChatRoute() {
        List<StModelProvider> legacyProviders = legacyProviderMapper.listAll();
        Map<String, Long> importedDeployments = new LinkedHashMap<>();
        int importedAccounts = 0;
        int skipped = 0;
        for (StModelProvider legacy : legacyProviders) {
            String plainKey = safe(legacy.getProxyPassword());
            if (plainKey.isBlank() || !Boolean.TRUE.equals(legacy.getEnabled())) {
                skipped++;
                continue;
            }
            String accountKey = trim("legacy_" + normalizeKey(legacy.getProviderKey()), 64);
            AiProviderAccount existing = mapper.findAccountByKey(accountKey);
            if (existing != null) {
                List<AiProviderDeployment> deployments = mapper.listDeploymentsByAccountId(existing.getId());
                deployments.stream()
                        .filter(item -> AiCapability.CHAT.name().equalsIgnoreCase(item.getCapability()))
                        .findFirst()
                        .ifPresent(item -> importedDeployments.put(legacy.getProviderKey(), item.getId()));
                skipped++;
                continue;
            }
            try {
                String source = safe(legacy.getStSource()).toLowerCase(Locale.ROOT);
                String rawBaseUrl = firstNonBlank(legacy.getCustomUrl(), legacy.getReverseProxy());
                String vendor = source;
                if ("custom".equals(source) || StringUtils.hasText(rawBaseUrl)) {
                    vendor = "custom";
                }
                String baseUrl = catalogService.normalizeBaseUrl(vendor, rawBaseUrl);

                AiProviderAccount account = new AiProviderAccount();
                account.setProviderKey(accountKey);
                account.setDisplayName(firstNonBlank(legacy.getDisplayName(), legacy.getProviderKey()) + "（旧路由导入）");
                account.setVendor(vendor);
                account.setBaseUrl(baseUrl);
                account.setApiKeyCipher(crypto.encrypt(plainKey));
                account.setEnabled(true);
                account.setConnectTimeoutSeconds(10);
                account.setRequestTimeoutSeconds(90);
                account.setNote("从 ST 旧聊天供应商复制，旧配置保留");
                mapper.insertAccount(account);

                AiProviderDeployment deployment = new AiProviderDeployment();
                deployment.setAccountId(account.getId());
                deployment.setCapability(AiCapability.CHAT.name());
                deployment.setProtocolType(AiProtocol.OPENAI_CHAT.name());
                deployment.setModelName(required(legacy.getModelName(), "模型 ID"));
                deployment.setVoiceName("");
                deployment.setEnabled(true);
                deployment.setFailureThreshold(legacy.getFailureThreshold() == null ? 3 : legacy.getFailureThreshold());
                deployment.setCooldownSeconds(legacy.getCooldownSeconds() == null ? 180 : legacy.getCooldownSeconds());
                mapper.insertDeployment(deployment);
                importedDeployments.put(legacy.getProviderKey(), deployment.getId());
                importedAccounts++;
            } catch (BusinessException ex) {
                skipped++;
            }
        }

        boolean routeCreated = false;
        if (mapper.findRouteByKey(AiCapability.CHAT.defaultRouteKey()) == null && !importedDeployments.isEmpty()) {
            StModelRoute legacyRoute = legacyRouteMapper.findBySceneKey(StModelRoutingService.DEFAULT_SCENE);
            LinkedHashSet<Long> orderedIds = new LinkedHashSet<>();
            if (legacyRoute != null) {
                addImportedDeployment(orderedIds, importedDeployments, legacyRoute.getPrimaryProviderKey());
                for (String key : safe(legacyRoute.getFallbackProviderKeys()).split("[|,]")) {
                    addImportedDeployment(orderedIds, importedDeployments, key);
                }
            }
            orderedIds.addAll(importedDeployments.values());
            AiRoute route = new AiRoute();
            route.setRouteKey(AiCapability.CHAT.defaultRouteKey());
            route.setDisplayName("文本聊天默认路由");
            route.setCapability(AiCapability.CHAT.name());
            route.setEnabled(true);
            route.setNote("从 ST 旧 default_chat 路由复制，运行开关仍保持关闭");
            mapper.insertRoute(route);
            int order = 1;
            for (Long deploymentId : orderedIds) {
                AiRouteMember member = new AiRouteMember();
                member.setRouteId(route.getId());
                member.setDeploymentId(deploymentId);
                member.setSortOrder(order++);
                mapper.insertRouteMember(member);
            }
            routeCreated = true;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("importedAccounts", importedAccounts);
        result.put("skipped", skipped);
        result.put("routeCreated", routeCreated);
        result.put("snapshot", adminSnapshot());
        return result;
    }

    @Transactional
    public void deleteDeployment(long id) {
        if (mapper.countRouteMembersForDeployment(id) > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先从能力路由中移除该模型");
        }
        mapper.deleteDeployment(id);
    }

    @Transactional
    public void deleteAccount(long id) {
        if (mapper.countDeploymentsForAccount(id) > 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先删除该账户下的能力模型");
        }
        mapper.deleteAccount(id);
    }

    @Transactional
    public void deleteRoute(long id) {
        mapper.deleteRouteMembers(id);
        mapper.deleteRoute(id);
    }

    @Transactional
    public List<ResolvedProvider> resolve(AiCapability capability) {
        return resolveRoute(capability.defaultRouteKey(), capability);
    }

    @Transactional
    public List<ResolvedProvider> resolveRoute(String routeKey, AiCapability capability) {
        LocalDateTime now = LocalDateTime.now();
        List<ResolvedProvider> active = new ArrayList<>();
        for (AiResolvedDeployment row : mapper.resolveRoute(routeKey)) {
            if (!capability.name().equalsIgnoreCase(row.getCapability())) {
                continue;
            }
            String key = decryptQuietly(row.getApiKeyCipher());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            ResolvedProvider provider = new ResolvedProvider(
                    row.getDeploymentId(), safe(row.getProviderKey()), safe(row.getDisplayName()),
                    safe(row.getVendor()), safe(row.getBaseUrl()), key, capability,
                    parseProtocol(row.getProtocolType(), capability), safe(row.getModelName()),
                    safe(row.getVoiceName()),
                    intValue(row.getConnectTimeoutSeconds(), 10, 1, 60),
                    intValue(row.getRequestTimeoutSeconds(), 90, 5, 600),
                    row.getSortOrder() == null ? active.size() + 1 : row.getSortOrder()
            );
            LocalDateTime openUntil = row.getCircuitOpenUntil();
            if (openUntil != null && openUntil.isAfter(now)) {
                continue;
            }
            int failures = Math.max(0, row.getConsecutiveFailures() == null ? 0 : row.getConsecutiveFailures());
            int threshold = Math.max(1, row.getFailureThreshold() == null ? 3 : row.getFailureThreshold());
            if (openUntil != null && failures >= threshold) {
                int leaseSeconds = Math.max(30, Math.min(600,
                        row.getCooldownSeconds() == null ? 180 : row.getCooldownSeconds()));
                if (mapper.tryAcquireHalfOpenProbe(
                        row.getDeploymentId(), now, now.plusSeconds(leaseSeconds)) != 1) {
                    continue;
                }
            }
            active.add(provider);
        }
        return List.copyOf(active);
    }

    @Transactional(readOnly = true)
    public DraftCredential resolveDraft(Map<String, Object> body) {
        Long accountId = longValue(body == null ? null : body.get("accountId"));
        Long deploymentId = longValue(body == null ? null : body.get("deploymentId"));
        AiProviderAccount stored = accountId == null ? null : mapper.findAccountById(accountId);
        String vendor = firstNonBlank(safe(body == null ? null : body.get("vendor")), stored == null ? "" : stored.getVendor());
        AiCapability capability = parseCapability(body == null ? null : body.get("capability"));
        String baseUrl = catalogService.normalizeBaseUrl(vendor,
                firstNonBlank(safe(body == null ? null : body.get("baseUrl")), stored == null ? "" : stored.getBaseUrl()));
        String apiKey = safe(body == null ? null : body.get("apiKey"));
        if (apiKey.isBlank() && stored != null) {
            apiKey = decryptQuietly(stored.getApiKeyCipher());
        }
        if (apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先填写 API Key");
        }
        return new DraftCredential(
                accountId, deploymentId, vendor, baseUrl, apiKey, capability,
                safe(body == null ? null : body.get("modelName")),
                safe(body == null ? null : body.get("voiceName")),
                intValue(body == null ? null : body.get("connectTimeoutSeconds"), 10, 1, 60),
                intValue(body == null ? null : body.get("requestTimeoutSeconds"), 90, 5, 600)
        );
    }

    public boolean shouldUseChatV2(Long conversationId) {
        AiRoutingRuntimeSettingsService.Settings settings = runtimeSettingsService.current();
        if (!settings.enabled() || settings.chatCanaryPercent() <= 0) {
            return false;
        }
        if (settings.chatCanaryPercent() >= 100) {
            return true;
        }
        long stable = conversationId == null ? 0L : conversationId;
        return Math.floorMod(Long.hashCode(stable), 100) < settings.chatCanaryPercent();
    }

    public boolean isChatFullyRolledOut() {
        AiRoutingRuntimeSettingsService.Settings settings = runtimeSettingsService.current();
        return settings.enabled() && settings.chatCanaryPercent() >= 100;
    }

    public boolean isCapabilityEnabled(AiCapability capability) {
        AiRoutingRuntimeSettingsService.Settings settings = runtimeSettingsService.current();
        if (!settings.enabled() || capability == null) {
            return false;
        }
        return switch (capability) {
            case CHAT -> settings.chatCanaryPercent() > 0;
            case VISION -> settings.visionEnabled();
            case IMAGE -> settings.imageEnabled();
            case TTS -> settings.ttsEnabled();
            case STT -> settings.sttEnabled();
        };
    }

    public void shadowCompareChat(List<StModelRoutingService.ResolvedProvider> legacy, List<ResolvedProvider> next) {
        if (!runtimeSettingsService.current().shadowEnabled() || next == null || next.isEmpty()) {
            return;
        }
        List<String> legacySummary = legacy == null ? List.of() : legacy.stream()
                .map(item -> item.providerKey() + ":" + item.modelName()).toList();
        List<String> nextSummary = next == null ? List.of() : next.stream()
                .map(item -> item.providerKey() + ":" + item.modelName()).toList();
        if (!legacySummary.equals(nextSummary)) {
            log.info("ai routing shadow mismatch capability=CHAT legacy={} v2={}", legacySummary, nextSummary);
        }
    }

    @Transactional
    public void recordSuccess(long deploymentId) {
        mapper.markDeploymentSuccess(deploymentId);
    }

    @Transactional
    public void recordFailure(long deploymentId, String message) {
        AiProviderDeployment current = mapper.findDeploymentByIdForUpdate(deploymentId);
        if (current == null) {
            return;
        }
        int failures = Math.max(0, current.getConsecutiveFailures() == null ? 0 : current.getConsecutiveFailures()) + 1;
        int threshold = Math.max(1, current.getFailureThreshold() == null ? 3 : current.getFailureThreshold());
        int cooldown = Math.max(30, current.getCooldownSeconds() == null ? 180 : current.getCooldownSeconds());
        LocalDateTime openUntil = failures >= threshold ? LocalDateTime.now().plusSeconds(cooldown) : current.getCircuitOpenUntil();
        mapper.markDeploymentFailure(deploymentId, failures, openUntil, trim(message, 500));
    }

    @Transactional
    public void recordConfigurationError(long deploymentId, String message) {
        mapper.markDeploymentStatus(deploymentId, "configuration_error", trim(message, 500));
    }

    private Map<String, Object> accountView(AiProviderAccount row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("providerKey", row.getProviderKey());
        item.put("displayName", row.getDisplayName());
        item.put("vendor", row.getVendor());
        item.put("baseUrl", row.getBaseUrl());
        String key = decryptQuietly(row.getApiKeyCipher());
        item.put("apiKeyConfigured", StringUtils.hasText(key));
        item.put("apiKeyMask", mask(key));
        item.put("enabled", row.getEnabled());
        item.put("connectTimeoutSeconds", row.getConnectTimeoutSeconds());
        item.put("requestTimeoutSeconds", row.getRequestTimeoutSeconds());
        item.put("versionNo", row.getVersionNo() == null ? 0L : row.getVersionNo());
        List<AiProviderDeployment> attached = mapper.listDeploymentsByAccountId(row.getId());
        item.put("deploymentCount", attached.size());
        item.put("capabilities", attached.stream().map(AiProviderDeployment::getCapability).distinct().toList());
        item.put("note", safe(row.getNote()));
        return item;
    }

    private static Map<String, Object> deploymentView(AiProviderDeployment row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.getId());
        item.put("accountId", row.getAccountId());
        item.put("capability", row.getCapability());
        item.put("protocolType", row.getProtocolType());
        item.put("modelName", row.getModelName());
        item.put("voiceName", safe(row.getVoiceName()));
        item.put("enabled", row.getEnabled());
        item.put("failureThreshold", row.getFailureThreshold());
        item.put("cooldownSeconds", row.getCooldownSeconds());
        item.put("consecutiveFailures", row.getConsecutiveFailures());
        item.put("circuitOpenUntil", row.getCircuitOpenUntil());
        item.put("lastHealthStatus", row.getLastHealthStatus());
        item.put("lastError", safe(row.getLastError()));
        item.put("lastUsedAt", row.getLastUsedAt());
        return item;
    }

    private String decryptQuietly(String cipherText) {
        try {
            return StringUtils.hasText(cipherText) ? crypto.decrypt(cipherText) : "";
        } catch (RuntimeException ex) {
            log.warn("cannot decrypt official AI provider key");
            return "";
        }
    }

    private static AiCapability parseCapability(Object value) {
        try {
            return AiCapability.parse(safe(value));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "能力类型不正确");
        }
    }

    private static AiProtocol parseProtocol(Object value, AiCapability capability) {
        try {
            return AiProtocol.parse(safe(value), capability);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "协议类型与当前能力不匹配");
        }
    }

    private static List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(AiRoutingService::longValue).filter(item -> item != null && item > 0).toList();
        }
        String raw = safe(value);
        if (raw.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : raw.split("[,|]")) {
            Long parsed = longValue(part);
            if (parsed != null && parsed > 0) result.add(parsed);
        }
        return result;
    }

    private static void addImportedDeployment(
            LinkedHashSet<Long> orderedIds,
            Map<String, Long> importedDeployments,
            String legacyProviderKey
    ) {
        Long deploymentId = importedDeployments.get(safe(legacyProviderKey));
        if (deploymentId != null) orderedIds.add(deploymentId);
    }

    private static String normalizeKey(Object value) {
        String key = safe(value).toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!key.matches("[a-z0-9_.]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Provider Key 格式不合法");
        }
        return key;
    }

    private static String required(Object value, String label) {
        String text = trim(value, 255);
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return text;
    }

    private static String trim(Object value, int max) {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max).trim();
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private static String mask(String secret) {
        String value = safe(secret);
        if (value.isBlank()) return "";
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            String text = safe(value);
            return text.isBlank() ? null : Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int intValue(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number number) parsed = number.intValue();
        else if (!safe(value).isBlank()) {
            try { parsed = Integer.parseInt(safe(value)); } catch (NumberFormatException ignored) {}
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        String text = safe(value);
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
