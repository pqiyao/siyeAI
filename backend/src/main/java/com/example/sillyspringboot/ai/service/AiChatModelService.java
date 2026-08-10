package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.entity.AiChatModelSettings;
import com.example.sillyspringboot.ai.entity.AiChatOffering;
import com.example.sillyspringboot.ai.entity.AiChatOfferingPrice;
import com.example.sillyspringboot.ai.entity.ChatModelPreference;
import com.example.sillyspringboot.ai.entity.UserAiChatModel;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.ai.mapper.AiRoutingMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiChatModelService {

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_BYOK = "BYOK";

    private static final Set<String> BILLING_MODES = Set.of(
            "FREE", "QUOTA_ONLY", "DIAMOND_ONLY", "GOLD_ONLY",
            "QUOTA_THEN_DIAMOND", "QUOTA_THEN_GOLD", "DIAMOND_AND_GOLD", "QUOTA_THEN_MIXED",
            "DIAMOND_OR_GOLD", "QUOTA_THEN_DIAMOND_OR_GOLD"
    );

    public record ResolvedChatModel(
            String source,
            Long offeringId,
            String offeringCode,
            String displayName,
            Long userModelId,
            String modelName,
            String routeKey,
            String billingMode,
            int quotaUnits,
            int diamondCost,
            int goldCost,
            long selectionVersion
    ) {
        public boolean platformOffering() { return SOURCE_SYSTEM.equals(source) && offeringId != null; }
        public boolean byok() { return SOURCE_BYOK.equals(source); }
    }

    private final AiChatModelMapper mapper;
    private final AiRoutingMapper routingMapper;
    private final AiRoutingService routingService;
    private final AppConversationMapper conversationMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final EntitlementPolicyService entitlementPolicyService;
    private final H5UserAiProviderService userAiProviderService;

    public AiChatModelService(
            AiChatModelMapper mapper,
            AiRoutingMapper routingMapper,
            AiRoutingService routingService,
            AppConversationMapper conversationMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            EntitlementPolicyService entitlementPolicyService,
            H5UserAiProviderService userAiProviderService
    ) {
        this.mapper = mapper;
        this.routingMapper = routingMapper;
        this.routingService = routingService;
        this.conversationMapper = conversationMapper;
        this.profileExtMapper = profileExtMapper;
        this.entitlementPolicyService = entitlementPolicyService;
        this.userAiProviderService = userAiProviderService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminSnapshot() {
        AiChatModelSettings settings = settings();
        List<Map<String, Object>> offerings = mapper.listOfferings().stream()
                .map(this::adminOfferingView)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", settingsView(settings));
        result.put("offerings", offerings);
        result.put("billingModes", billingModeCatalog());
        return result;
    }

    @Transactional
    public Map<String, Object> saveSettings(Map<String, Object> body) {
        AiChatModelSettings row = settings();
        row.setEnabled(bool(body == null ? null : body.get("enabled"), false));
        row.setCanaryPercent(intValue(body == null ? null : body.get("canaryPercent"), 0, 0, 100));
        validateLiveDefaultOffering(row);
        mapper.updateSettings(row);
        return settingsView(settings());
    }

    @Transactional
    public Map<String, Object> saveOfferingBundle(Map<String, Object> body) {
        Object routeValue = body == null ? null : body.get("route");
        Object offeringValue = body == null ? null : body.get("offering");
        if (!(routeValue instanceof Map<?, ?> routeRaw) || !(offeringValue instanceof Map<?, ?> offeringRaw)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模型路由和模型方案不能为空");
        }
        Map<String, Object> route = stringKeyMap(routeRaw);
        Map<String, Object> offering = stringKeyMap(offeringRaw);
        String routeKey = safe(route.get("routeKey")).toLowerCase(Locale.ROOT);
        String offeringRouteKey = safe(offering.get("routeKey")).toLowerCase(Locale.ROOT);
        if (routeKey.isBlank() || !routeKey.equals(offeringRouteKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模型方案与 fallback 路由不一致");
        }
        routingService.saveRoute(route);
        return saveOffering(offering);
    }

    @Transactional
    public Map<String, Object> saveOffering(Map<String, Object> body) {
        Long id = longValue(body == null ? null : body.get("id"));
        AiChatOffering row = id == null ? null : mapper.findOfferingById(id);
        if (id != null && row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公开聊天模型不存在");
        }
        boolean creating = row == null;
        if (creating) row = new AiChatOffering();

        String code = safe(body == null ? null : body.get("offeringCode")).toLowerCase(Locale.ROOT);
        if (creating) {
            if (!code.matches("[a-z0-9][a-z0-9_.-]{1,63}")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "公开编码需为 2-64 位小写字母、数字、点、横线或下划线");
            }
            AiChatOffering duplicate = mapper.findOfferingByCode(code);
            if (duplicate != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "公开模型编码已存在");
            }
            row.setOfferingCode(code);
        }

        String routeKey = required(body == null ? null : body.get("routeKey"), "CHAT 路由").toLowerCase(Locale.ROOT);
        var route = routingMapper.findRouteByKey(routeKey);
        if (route == null || !AiCapability.CHAT.name().equalsIgnoreCase(route.getCapability())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "只能绑定已存在的 CHAT 路由");
        }
        if (bool(body == null ? null : body.get("enabled"), false)
                && (!Boolean.TRUE.equals(route.getEnabled()) || routingMapper.listRouteMembers(route.getId()).isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "发布前请先启用路由并配置至少一个 CHAT 供应商模型");
        }

        row.setDisplayName(required(body == null ? null : body.get("displayName"), "展示名称"));
        row.setShortDescription(trim(body == null ? null : body.get("shortDescription"), 255));
        row.setDescription(trim(body == null ? null : body.get("description"), 1000));
        row.setTags(normalizeTags(body == null ? null : body.get("tags")));
        row.setBadge(trim(body == null ? null : body.get("badge"), 64));
        row.setContextLabel(trim(body == null ? null : body.get("contextLabel"), 64));
        row.setSpeedLevel(intValue(body == null ? null : body.get("speedLevel"), 3, 1, 5));
        row.setQualityLevel(intValue(body == null ? null : body.get("qualityLevel"), 3, 1, 5));
        row.setRouteKey(routeKey);
        row.setVipMinLevel(intValue(body == null ? null : body.get("vipMinLevel"), 0, 0, 99));
        row.setRecommended(bool(body == null ? null : body.get("recommended"), false));
        row.setDefaultOffering(bool(body == null ? null : body.get("defaultOffering"), false));
        row.setSortOrder(intValue(body == null ? null : body.get("sortOrder"), 100, 0, 100000));
        row.setEnabled(bool(body == null ? null : body.get("enabled"), false));
        row.setMaintenance(bool(body == null ? null : body.get("maintenance"), false));

        List<AiChatOfferingPrice> prices = parsePrices(body == null ? null : body.get("prices"));
        if (prices.stream().noneMatch(item -> nvl(item.getVipLevel()) == 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "至少配置一条普通用户价格");
        }

        try {
            if (creating) {
                mapper.insertOffering(row);
            } else if (mapper.updateOffering(row) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "模型方案已被其他管理员修改，请刷新后重试");
            }
            if (Boolean.TRUE.equals(row.getDefaultOffering())) {
                mapper.clearDefaultOffering(row.getId());
            }
            mapper.deletePrices(row.getId());
            for (AiChatOfferingPrice price : prices) {
                price.setOfferingId(row.getId());
                mapper.insertPrice(price);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "模型编码或会员价格层级重复");
        }
        validateLiveDefaultOffering(settings());
        return adminOfferingView(mapper.findOfferingById(row.getId()));
    }

    @Transactional
    public void deleteOffering(long id) {
        AiChatOffering row = mapper.findOfferingById(id);
        if (row == null) return;
        if (Boolean.TRUE.equals(row.getEnabled())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先停用公开模型，再执行删除");
        }
        mapper.deletePrices(id);
        mapper.deleteOffering(id);
        validateLiveDefaultOffering(settings());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userCatalog(long userId, Long conversationId) {
        long safeConversationId = conversationId == null ? 0 : Math.max(0, conversationId);
        long branchId = resolveBranchId(userId, safeConversationId);
        int vipLevel = vipLevel(userId);
        boolean featureEnabled = enabledForUser(userId);
        List<Map<String, Object>> offerings = mapper.listPublishedOfferings().stream()
                .map(item -> publicOfferingView(item, vipLevel))
                .toList();
        boolean byokReady = hasActiveByok(userId);
        List<Map<String, Object>> byokModels = mapper.listUserModels(userId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(item -> userModelView(item, byokReady))
                .toList();
        ResolvedChatModel current = featureEnabled
                ? resolveInternal(userId, safeConversationId, branchId, "", "", false)
                : null;

        AppH5UserProfileExt ext = profileExtMapper.findByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", featureEnabled);
        result.put("platformModels", offerings);
        result.put("byokModels", byokModels);
        result.put("current", resolvedView(current));
        result.put("conversationId", safeConversationId);
        result.put("branchId", branchId);
        result.put("wallet", Map.of(
                "diamonds", ext == null ? 0 : nvl(ext.getScore()),
                "gold", ext == null ? 0 : nvl(ext.getGoldCoin())
        ));
        result.put("message", featureEnabled ? "" : "聊天模型选择尚未对当前账号开放");
        return result;
    }

    @Transactional
    public Map<String, Object> select(long userId, Long conversationId, String source, String ref) {
        if (!enabledForUser(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "聊天模型选择尚未对当前账号开放");
        }
        long safeConversationId = conversationId == null ? 0 : Math.max(0, conversationId);
        long branchId = resolveBranchId(userId, safeConversationId);
        ResolvedChatModel resolved = resolveInternal(userId, safeConversationId, branchId, source, ref, true);
        savePreference(userId, safeConversationId, branchId, resolved);
        return resolvedView(resolved);
    }

    @Transactional(readOnly = true)
    public ResolvedChatModel resolveForGeneration(
            long userId,
            long conversationId,
            String source,
            String ref
    ) {
        return resolveForGeneration(userId, conversationId, source, ref, null);
    }

    @Transactional(readOnly = true)
    public ResolvedChatModel resolveForGeneration(
            long userId,
            long conversationId,
            String source,
            String ref,
            Long expectedSelectionVersion
    ) {
        if (!enabledForUser(userId)) return null;
        long branchId = resolveBranchId(userId, conversationId);
        ResolvedChatModel resolved = resolveInternal(userId, conversationId, branchId, source, ref, true);
        if (expectedSelectionVersion != null && resolved != null && resolved.platformOffering()
                && expectedSelectionVersion.longValue() != resolved.selectionVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型价格或配置已更新，请刷新后确认");
        }
        return resolved;
    }

    @Transactional
    public List<Map<String, Object>> saveUserModels(long userId, Map<String, Object> body) {
        Object raw = body == null ? null : body.get("models");
        List<?> values = raw instanceof List<?> list ? list : List.of();
        if (values.size() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "最多保存 50 个聊天模型");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        List<Long> keptIds = new ArrayList<>();
        Long requestedDefaultId = longValue(body == null ? null : body.get("defaultModelId"));
        String requestedDefaultName = safe(body == null ? null : body.get("defaultModelName"));
        int order = 0;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> item)) continue;
            String modelName = trim(item.get("modelName"), 255);
            if (modelName.isBlank() || !names.add(modelName.toLowerCase(Locale.ROOT))) continue;
            UserAiChatModel row = mapper.findUserModelByName(userId, modelName);
            if (row == null) row = new UserAiChatModel();
            row.setUserId(userId);
            row.setModelName(modelName);
            row.setDisplayName(trim(item.get("displayName"), 128));
            row.setSortOrder(intValue(item.get("sortOrder"), order++, 0, 100000));
            row.setDefaultModel(false);
            row.setEnabled(bool(item.get("enabled"), true));
            if (row.getLastTestStatus() == null) row.setLastTestStatus("unknown");
            if (row.getId() == null) mapper.insertUserModel(row); else mapper.updateUserModel(row);
            keptIds.add(row.getId());
        }
        if (keptIds.isEmpty()) mapper.deleteAllUserModels(userId); else mapper.deleteUserModelsNotIn(userId, keptIds);

        UserAiChatModel selectedDefault = null;
        if (requestedDefaultId != null) selectedDefault = mapper.findUserModel(userId, requestedDefaultId);
        if (selectedDefault == null && !requestedDefaultName.isBlank()) {
            selectedDefault = mapper.findUserModelByName(userId, requestedDefaultName);
        }
        if (selectedDefault == null && !keptIds.isEmpty()) selectedDefault = mapper.findUserModel(userId, keptIds.get(0));
        mapper.clearDefaultUserModel(userId, selectedDefault == null ? null : selectedDefault.getId());
        if (selectedDefault != null) {
            selectedDefault.setDefaultModel(true);
            mapper.updateUserModel(selectedDefault);
        }
        return mapper.listUserModels(userId).stream().map(AiChatModelService::userModelView).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> userModels(long userId) {
        return mapper.listUserModels(userId).stream().map(AiChatModelService::userModelView).toList();
    }

    public boolean enabledForUser(long userId) {
        AiChatModelSettings settings = settings();
        if (!Boolean.TRUE.equals(settings.getEnabled())) return false;
        int percent = intValue(settings.getCanaryPercent(), 0, 0, 100);
        return percent >= 100 || (percent > 0 && Math.floorMod(Long.hashCode(userId), 100) < percent);
    }

    private ResolvedChatModel resolveInternal(
            long userId,
            long conversationId,
            long branchId,
            String requestedSource,
            String requestedRef,
            boolean failWhenUnavailable
    ) {
        String source = normalizeSource(requestedSource);
        String ref = safe(requestedRef);
        boolean explicitSource = !safe(requestedSource).isBlank();
        boolean explicitRef = !ref.isBlank();
        ChatModelPreference preference = null;
        if (source.isBlank()) {
            preference = mapper.findPreference(userId, conversationId, branchId);
            if (preference == null && (conversationId != 0 || branchId != 0)) {
                preference = mapper.findPreference(userId, 0, 0);
            }
            if (preference != null) source = normalizeSource(preference.getSourceType());
            else if (userAiProviderService.isCustomModeSelectedForUser(userId)) source = SOURCE_BYOK;
        }

        if (SOURCE_BYOK.equals(source)) {
            if (!userAiProviderService.isCustomModeAllowedForUser(userId)) {
                source = SOURCE_SYSTEM;
                ref = "";
                explicitSource = false;
                explicitRef = false;
                preference = null;
            }
        }

        if (SOURCE_BYOK.equals(source)) {
            UserAiChatModel model = null;
            Long requestedId = longValue(ref);
            if (requestedId != null) model = mapper.findUserModel(userId, requestedId);
            if (model == null && !explicitRef && preference != null && preference.getUserModelId() != null) {
                model = mapper.findUserModel(userId, preference.getUserModelId());
            }
            if (model == null && !explicitRef) model = mapper.findDefaultUserModel(userId);
            if (model != null && Boolean.TRUE.equals(model.getEnabled())
                    && userAiProviderService.resolveActiveOverrideForUser(userId) != null) {
                return new ResolvedChatModel(
                        SOURCE_BYOK, null, "", displayName(model), model.getId(), model.getModelName(), "",
                        "BYOK", 0, 0, 0, preference == null ? 0 : nvl(preference.getVersionNo())
                );
            }
            if (failWhenUnavailable) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "自定义 API 或所选聊天模型不可用，请先到 AI 设置检查");
            }
            return null;
        }

        AiChatOffering offering = null;
        if (SOURCE_SYSTEM.equals(source) && !ref.isBlank()) offering = mapper.findOfferingByCode(ref.toLowerCase(Locale.ROOT));
        if (SOURCE_SYSTEM.equals(source) && explicitRef && offering == null) {
            if (failWhenUnavailable) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "所选平台模型不存在或已下架");
            }
            return null;
        }
        if (offering == null && preference != null && preference.getOfferingId() != null) {
            offering = mapper.findOfferingById(preference.getOfferingId());
        }
        if (offering == null && (!explicitSource || SOURCE_SYSTEM.equals(source))) offering = mapper.findDefaultOffering();
        if (offering == null) {
            if (failWhenUnavailable) throw new BusinessException(ErrorCode.SERVICE_BUSY, "平台尚未发布可用聊天模型");
            return null;
        }
        try {
            validateOfferingAvailable(offering, vipLevel(userId));
        } catch (BusinessException ex) {
            if (failWhenUnavailable) throw ex;
            return null;
        }
        AiChatOfferingPrice price = resolvePrice(offering.getId(), vipLevel(userId));
        return new ResolvedChatModel(
                SOURCE_SYSTEM, offering.getId(), offering.getOfferingCode(), offering.getDisplayName(), null, "",
                offering.getRouteKey(), price.getBillingMode(), nvl(price.getQuotaUnits()),
                nvl(price.getDiamondCost()), nvl(price.getGoldCost()),
                nvl(offering.getVersionNo())
        );
    }

    private void savePreference(long userId, long conversationId, long branchId, ResolvedChatModel resolved) {
        ChatModelPreference row = mapper.findPreference(userId, conversationId, branchId);
        if (row == null) {
            row = new ChatModelPreference();
            row.setUserId(userId);
            row.setConversationId(conversationId);
            row.setBranchId(branchId);
            row.setVersionNo(0L);
        }
        row.setSourceType(resolved.source());
        row.setOfferingId(resolved.offeringId());
        row.setUserModelId(resolved.userModelId());
        if (row.getId() == null) {
            try {
                mapper.insertPreference(row);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "模型选择已在其他设备创建，请刷新后重试",
                        ex
                );
            }
        } else if (mapper.updatePreference(row) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "模型选择已在其他设备更新，请重试");
        }
    }

    private void validateLiveDefaultOffering(AiChatModelSettings config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())
                || nvl(config.getCanaryPercent()) <= 0) {
            return;
        }
        AiChatOffering defaultOffering = mapper.findDefaultOffering();
        if (defaultOffering == null
                || !routingService.isCapabilityEnabled(AiCapability.CHAT)
                || !routingService.isRouteConfigured(defaultOffering.getRouteKey(), AiCapability.CHAT)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "聊天模型选择已开放，必须保留一个已发布、非维护且路由可用的默认聊天模型"
            );
        }
    }

    private long resolveBranchId(long userId, long conversationId) {
        if (conversationId <= 0) return 0;
        AppConversation conversation = conversationMapper.findByIdForUser(conversationId, userId);
        if (conversation == null) throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        return conversation.getActiveBranchId() == null ? 0 : conversation.getActiveBranchId();
    }

    private void validateOfferingAvailable(AiChatOffering offering, int vipLevel) {
        if (!Boolean.TRUE.equals(offering.getEnabled())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "所选平台模型已下架，请重新选择");
        }
        if (Boolean.TRUE.equals(offering.getMaintenance())) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "所选平台模型正在维护，请选择其他模型");
        }
        if (vipLevel < nvl(offering.getVipMinLevel())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前会员等级无法使用这个模型");
        }
        var route = routingMapper.findRouteByKey(offering.getRouteKey());
        if (route == null || !Boolean.TRUE.equals(route.getEnabled())
                || !AiCapability.CHAT.name().equalsIgnoreCase(route.getCapability())
                || routingMapper.listRouteMembers(route.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "所选平台模型暂时没有可用路由");
        }
        if (!routingService.isCapabilityEnabled(AiCapability.CHAT)) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "平台新聊天路由尚未启用");
        }
    }

    private AiChatOfferingPrice resolvePrice(long offeringId, int vipLevel) {
        return mapper.listPrices(offeringId).stream()
                .filter(item -> nvl(item.getVipLevel()) <= vipLevel)
                .max(Comparator.comparingInt(item -> nvl(item.getVipLevel())))
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "平台模型价格未配置"));
    }

    private int vipLevel(long userId) {
        AppH5UserProfileExt ext = profileExtMapper.findByUserId(userId);
        return entitlementPolicyService.effectiveVipLevel(ext);
    }

    private boolean hasActiveByok(long userId) {
        try {
            return userAiProviderService.resolveActiveOverrideForUser(userId) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private AiChatModelSettings settings() {
        AiChatModelSettings row = mapper.findSettings();
        if (row != null) return row;
        row = new AiChatModelSettings();
        row.setId(1L);
        row.setEnabled(false);
        row.setShadowEnabled(true);
        row.setCanaryPercent(0);
        return row;
    }

    private Map<String, Object> adminOfferingView(AiChatOffering row) {
        Map<String, Object> view = offeringBaseView(row);
        view.put("prices", mapper.listPrices(row.getId()).stream().map(AiChatModelService::priceView).toList());
        var route = routingMapper.findRouteByKey(row.getRouteKey());
        view.put("routeReady", routingService.isRouteConfigured(row.getRouteKey(), AiCapability.CHAT));
        view.put("routeMemberCount", route == null ? 0 : routingMapper.listRouteMembers(route.getId()).size());
        return view;
    }

    private Map<String, Object> publicOfferingView(AiChatOffering row, int vipLevel) {
        Map<String, Object> view = offeringBaseView(row);
        AiChatOfferingPrice price = resolvePriceOrNull(row.getId(), vipLevel);
        if (price != null) view.putAll(priceView(price));
        else view.putAll(priceView(price("", 0, 0, 0)));

        boolean access = vipLevel >= nvl(row.getVipMinLevel());
        var route = routingMapper.findRouteByKey(row.getRouteKey());
        boolean routeReady = routingService.isRouteConfigured(row.getRouteKey(), AiCapability.CHAT);
        boolean capabilityEnabled = routingService.isCapabilityEnabled(AiCapability.CHAT);
        String unavailableReason = !access ? "会员等级不足"
                : Boolean.TRUE.equals(row.getMaintenance()) ? "维护中"
                : price == null ? "价格未配置"
                : !capabilityEnabled ? "平台聊天路由未开放"
                : !routeReady ? "模型线路暂不可用"
                : "";
        view.put("available", unavailableReason.isBlank());
        view.put("unavailableReason", unavailableReason);
        view.put("priceText", price == null ? "暂不可用" : priceText(price));
        view.remove("routeKey");
        return view;
    }

    private AiChatOfferingPrice resolvePriceOrNull(long offeringId, int vipLevel) {
        return mapper.listPrices(offeringId).stream()
                .filter(item -> nvl(item.getVipLevel()) <= vipLevel)
                .max(Comparator.comparingInt(item -> nvl(item.getVipLevel())))
                .orElse(null);
    }

    private Map<String, Object> offeringBaseView(AiChatOffering row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("offeringCode", safe(row.getOfferingCode()));
        view.put("displayName", safe(row.getDisplayName()));
        view.put("shortDescription", safe(row.getShortDescription()));
        view.put("description", safe(row.getDescription()));
        view.put("tags", splitTags(row.getTags()));
        view.put("badge", safe(row.getBadge()));
        view.put("contextLabel", safe(row.getContextLabel()));
        view.put("speedLevel", nvl(row.getSpeedLevel()));
        view.put("qualityLevel", nvl(row.getQualityLevel()));
        view.put("routeKey", safe(row.getRouteKey()));
        view.put("vipMinLevel", nvl(row.getVipMinLevel()));
        view.put("recommended", Boolean.TRUE.equals(row.getRecommended()));
        view.put("defaultOffering", Boolean.TRUE.equals(row.getDefaultOffering()));
        view.put("sortOrder", nvl(row.getSortOrder()));
        view.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
        view.put("maintenance", Boolean.TRUE.equals(row.getMaintenance()));
        view.put("versionNo", nvl(row.getVersionNo()));
        return view;
    }

    private static Map<String, Object> priceView(AiChatOfferingPrice row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("vipLevel", nvl(row.getVipLevel()));
        view.put("billingMode", safe(row.getBillingMode()));
        view.put("quotaUnits", nvl(row.getQuotaUnits()));
        view.put("diamondCost", nvl(row.getDiamondCost()));
        view.put("goldCost", nvl(row.getGoldCost()));
        return view;
    }

    private static Map<String, Object> userModelView(UserAiChatModel row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", row.getId());
        view.put("modelName", safe(row.getModelName()));
        view.put("displayName", displayName(row));
        view.put("sortOrder", nvl(row.getSortOrder()));
        view.put("defaultModel", Boolean.TRUE.equals(row.getDefaultModel()));
        view.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
        view.put("lastTestStatus", safe(row.getLastTestStatus()));
        return view;
    }

    private static Map<String, Object> userModelView(UserAiChatModel row, boolean available) {
        Map<String, Object> view = userModelView(row);
        view.put("available", available);
        view.put("unavailableReason", available ? "" : "请先启用并保存自定义 API 配置");
        return view;
    }

    private static Map<String, Object> resolvedView(ResolvedChatModel resolved) {
        if (resolved == null) return Map.of();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("source", resolved.source());
        view.put("ref", resolved.byok() ? String.valueOf(resolved.userModelId()) : resolved.offeringCode());
        view.put("displayName", resolved.displayName());
        view.put("billingMode", resolved.billingMode());
        view.put("quotaUnits", resolved.quotaUnits());
        view.put("diamondCost", resolved.diamondCost());
        view.put("goldCost", resolved.goldCost());
        view.put("priceText", resolved.byok() ? "使用自己的 API" : priceText(
                price(resolved.billingMode(), resolved.quotaUnits(), resolved.diamondCost(), resolved.goldCost())));
        view.put("selectionVersion", resolved.selectionVersion());
        return view;
    }

    private static List<Map<String, Object>> billingModeCatalog() {
        return List.of(
                Map.of("value", "FREE", "label", "免费", "description", "不扣次数和钱包"),
                Map.of("value", "QUOTA_ONLY", "label", "仅聊天次数", "description", "次数不足时拒绝生成"),
                Map.of("value", "DIAMOND_ONLY", "label", "仅钻石", "description", "每次固定扣钻石"),
                Map.of("value", "GOLD_ONLY", "label", "仅金币", "description", "每次固定扣金币"),
                Map.of("value", "QUOTA_THEN_DIAMOND", "label", "次数优先，后扣钻石", "description", "次数不足时改扣钻石"),
                Map.of("value", "QUOTA_THEN_GOLD", "label", "次数优先，后扣金币", "description", "次数不足时改扣金币"),
                Map.of("value", "DIAMOND_AND_GOLD", "label", "钻石和金币", "description", "每次同时扣两种余额"),
                Map.of("value", "QUOTA_THEN_MIXED", "label", "次数优先，后扣混合余额", "description", "次数不足时同时扣钻石和金币"),
                Map.of("value", "DIAMOND_OR_GOLD", "label", "钻石或金币", "description", "优先扣钻石，钻石不足时整笔改扣金币"),
                Map.of("value", "QUOTA_THEN_DIAMOND_OR_GOLD", "label", "次数优先，后扣钻石或金币", "description", "次数不足后优先扣钻石，钻石不足时整笔改扣金币")
        );
    }

    private static Map<String, Object> settingsView(AiChatModelSettings row) {
        return Map.of(
                "enabled", Boolean.TRUE.equals(row.getEnabled()),
                "canaryPercent", nvl(row.getCanaryPercent())
        );
    }

    private static List<AiChatOfferingPrice> parsePrices(Object value) {
        List<?> list = value instanceof List<?> items ? items : List.of();
        List<AiChatOfferingPrice> result = new ArrayList<>();
        Set<Integer> levels = new LinkedHashSet<>();
        for (Object itemValue : list) {
            if (!(itemValue instanceof Map<?, ?> item)) continue;
            String mode = safe(item.get("billingMode")).toUpperCase(Locale.ROOT);
            if (!BILLING_MODES.contains(mode)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "计费模式不支持：" + mode);
            }
            int level = intValue(item.get("vipLevel"), 0, 0, 99);
            if (!levels.add(level)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "会员价格层级重复");
            AiChatOfferingPrice price = new AiChatOfferingPrice();
            price.setVipLevel(level);
            price.setBillingMode(mode);
            price.setQuotaUnits(intValue(item.get("quotaUnits"), defaultQuotaUnits(mode), 0, 1000));
            price.setDiamondCost(intValue(item.get("diamondCost"), 0, 0, 1000000));
            price.setGoldCost(intValue(item.get("goldCost"), 0, 0, 1000000));
            validatePrice(price);
            result.add(price);
        }
        return result;
    }

    private static void validatePrice(AiChatOfferingPrice price) {
        String mode = price.getBillingMode();
        int quota = nvl(price.getQuotaUnits());
        int diamonds = nvl(price.getDiamondCost());
        int gold = nvl(price.getGoldCost());
        if ((mode.contains("QUOTA") && quota <= 0)
                || (mode.contains("DIAMOND") && diamonds <= 0)
                || (mode.contains("GOLD") && gold <= 0)
                || ("DIAMOND_AND_GOLD".equals(mode) && (diamonds <= 0 || gold <= 0))
                || ("DIAMOND_OR_GOLD".equals(mode) && (diamonds <= 0 || gold <= 0))
                || ("QUOTA_THEN_MIXED".equals(mode) && (quota <= 0 || diamonds <= 0 || gold <= 0))
                || ("QUOTA_THEN_DIAMOND_OR_GOLD".equals(mode) && (quota <= 0 || diamonds <= 0 || gold <= 0))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "计费模式与次数/钻石/金币数值不匹配");
        }
    }

    private static int defaultQuotaUnits(String mode) { return mode.contains("QUOTA") ? 1 : 0; }

    private static String priceText(AiChatOfferingPrice price) {
        String mode = safe(price.getBillingMode());
        int q = nvl(price.getQuotaUnits());
        int d = nvl(price.getDiamondCost());
        int g = nvl(price.getGoldCost());
        return switch (mode) {
            case "FREE" -> "免费";
            case "QUOTA_ONLY" -> q + "次";
            case "DIAMOND_ONLY" -> d + "钻石/次";
            case "GOLD_ONLY" -> g + "金币/次";
            case "QUOTA_THEN_DIAMOND" -> q + "次，用完后" + d + "钻石";
            case "QUOTA_THEN_GOLD" -> q + "次，用完后" + g + "金币";
            case "DIAMOND_AND_GOLD" -> d + "钻石 + " + g + "金币/次";
            case "QUOTA_THEN_MIXED" -> q + "次，用完后" + d + "钻石 + " + g + "金币";
            case "DIAMOND_OR_GOLD" -> d + "钻石 或 " + g + "金币/次";
            case "QUOTA_THEN_DIAMOND_OR_GOLD" -> q + "次，用完后" + d + "钻石 或 " + g + "金币";
            default -> "--";
        };
    }

    private static AiChatOfferingPrice price(String mode, int quota, int diamonds, int gold) {
        AiChatOfferingPrice row = new AiChatOfferingPrice();
        row.setBillingMode(mode);
        row.setQuotaUnits(quota);
        row.setDiamondCost(diamonds);
        row.setGoldCost(gold);
        return row;
    }

    private static String normalizeSource(Object value) {
        String source = safe(value).toUpperCase(Locale.ROOT);
        return SOURCE_SYSTEM.equals(source) || SOURCE_BYOK.equals(source) ? source : "";
    }

    private static String normalizeTags(Object value) {
        List<String> tags = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String tag = trim(item, 32);
                if (!tag.isBlank() && !tags.contains(tag)) tags.add(tag);
                if (tags.size() >= 8) break;
            }
        } else {
            for (String item : safe(value).split("[|,，]")) {
                String tag = trim(item, 32);
                if (!tag.isBlank() && !tags.contains(tag)) tags.add(tag);
                if (tags.size() >= 8) break;
            }
        }
        return String.join("|", tags);
    }

    private static List<String> splitTags(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return List.of(value.split("\\|"));
    }

    private static String displayName(UserAiChatModel row) {
        return StringUtils.hasText(row.getDisplayName()) ? row.getDisplayName().trim() : safe(row.getModelName());
    }
    private static String required(Object value, String label) {
        String text = trim(value, 255);
        if (text.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        return text;
    }
    private static String trim(Object value, int max) {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max).trim();
    }
    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { String text = safe(value); return text.isBlank() ? null : Long.parseLong(text); }
        catch (NumberFormatException ignored) { return null; }
    }
    private static int intValue(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number number) parsed = number.intValue();
        else if (!safe(value).isBlank()) try { parsed = Integer.parseInt(safe(value)); } catch (NumberFormatException ignored) {}
        return Math.max(min, Math.min(max, parsed));
    }
    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        String text = safe(value);
        return text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }
    private static int nvl(Integer value) { return value == null ? 0 : value; }
    private static long nvl(Long value) { return value == null ? 0 : value; }
    private static String safe(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
