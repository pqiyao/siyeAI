package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoice;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceBinding;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceBindingMapper;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.net.MediaPayloadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UserTtsVoiceService {

    private static final Logger log = LoggerFactory.getLogger(UserTtsVoiceService.class);
    private static final int MAX_AUDIO_BYTES = 15 * 1024 * 1024;
    private static final int MIN_DURATION_MS = 5_000;
    private static final int MAX_DURATION_MS = 60_000;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROVISIONING = "PROVISIONING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final long STALE_PROVISIONING_MINUTES = 3L;
    private static final String STALE_PROVISIONING_ERROR = "创建任务超过 3 分钟未完成，系统已自动结束，请重新创建";
    private static final String ADMIN_FINISHED_ERROR = "创建任务已由管理员结束，请重新创建";

    public record RuntimeVoice(String voiceUri, String modelName) {}

    private final AppUserTtsVoiceMapper voiceMapper;
    private final AppUserTtsVoiceBindingMapper bindingMapper;
    private final AppH5UserProfileExtMapper profileMapper;
    private final EntitlementPolicyService entitlementPolicyService;
    private final H5UserAiProviderService userAiProviderService;
    private final TtsVoiceProvisionService provisionService;
    private final UserTtsVoiceReservationService reservationService;
    private final MediaConcurrencyGate mediaGate;
    private final H5EntitlementService entitlementService;
    private final CharacterStudioMapper characterStudioMapper;

    public UserTtsVoiceService(
            AppUserTtsVoiceMapper voiceMapper,
            AppUserTtsVoiceBindingMapper bindingMapper,
            AppH5UserProfileExtMapper profileMapper,
            EntitlementPolicyService entitlementPolicyService,
            H5UserAiProviderService userAiProviderService,
            TtsVoiceProvisionService provisionService,
            UserTtsVoiceReservationService reservationService,
            MediaConcurrencyGate mediaGate,
            H5EntitlementService entitlementService,
            CharacterStudioMapper characterStudioMapper
    ) {
        this.voiceMapper = voiceMapper;
        this.bindingMapper = bindingMapper;
        this.profileMapper = profileMapper;
        this.entitlementPolicyService = entitlementPolicyService;
        this.userAiProviderService = userAiProviderService;
        this.provisionService = provisionService;
        this.reservationService = reservationService;
        this.mediaGate = mediaGate;
        this.entitlementService = entitlementService;
        this.characterStudioMapper = characterStudioMapper;
    }

    public Map<String, Object> overview(long userId) {
        recoverStaleProvisioningForUser(userId);
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        int limit = limitForUser(policy, userId);
        int used = Math.max(0, voiceMapper.countOccupyingByUserId(userId));
        H5UserAiProviderService.UserTtsSettings settings = userAiProviderService.resolveActiveTtsSettingsForUser(userId);
        String configReason = validateRuntimeReason(settings);
        boolean enabled = policy.isUserVoiceCreationEnabled();
        boolean canCreate = enabled && used < limit && configReason.isBlank();
        String denyReason = "";
        if (!enabled) denyReason = "管理员暂未开放自建音色";
        else if (limit <= 0) denyReason = "当前会员权益不支持自建音色";
        else if (used >= limit) denyReason = "已达到当前权益的音色数量上限";
        else denyReason = configReason;

        Long globalVoiceId = bindingVoiceId(userId, "GLOBAL", 0, 0);
        List<Map<String, Object>> voices = voiceMapper.listByUserId(userId).stream()
                .map(row -> toUserMap(row, settings, globalVoiceId))
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("featureEnabled", enabled);
        data.put("canCreate", canCreate);
        data.put("denyReason", denyReason);
        data.put("limit", limit);
        data.put("used", used);
        data.put("remaining", Math.max(0, limit - used));
        data.put("providerSource", settings == null ? "" : safe(settings.providerSource()));
        data.put("modelName", settings == null ? "" : safe(settings.modelName()));
        data.put("globalVoiceId", globalVoiceId == null ? 0L : globalVoiceId);
        data.put("voices", voices);
        return data;
    }

    public Map<String, Object> create(
            long userId,
            String requestId,
            String displayName,
            String sampleText,
            int durationMs,
            MultipartFile file
    ) {
        String safeRequestId = normalizeRequestId(requestId);
        recoverStaleProvisioningForUser(userId);
        AppUserTtsVoice existing = voiceMapper.findByUserIdAndRequestId(userId, safeRequestId);
        if (existing != null) return toUserMap(existing, activeSettings(userId), null);

        byte[] audio = readAndValidateAudio(file);
        String mimeType = MediaPayloadValidator.requireAudio(audio, file == null ? "" : file.getContentType());
        long actualDurationMs = AudioDurationInspector.durationMillis(audio, mimeType);
        if (actualDurationMs < MIN_DURATION_MS || actualDurationMs > MAX_DURATION_MS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频时长需为 5 到 60 秒");
        }
        String safeDisplayName = requiredText(displayName, 64, "请填写音色名称");
        String safeSampleText = requiredText(sampleText, 255, "请填写参考音频中准确的朗读文本");
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        if (!policy.isUserVoiceCreationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员暂未开放自建音色");
        }
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        TtsVoiceProvisionService.TtsRuntimeContext runtime = runtimeContext(settings);

        try (MediaConcurrencyGate.Lease ignored = mediaGate.acquire(
                MediaConcurrencyGate.Capability.VOICE_CLONE, userId, safeRequestId)) {
            existing = voiceMapper.findByUserIdAndRequestId(userId, safeRequestId);
            if (existing != null) return toUserMap(existing, settings, null);
            int limit = limitForUser(policy, userId);

            AppUserTtsVoice row = new AppUserTtsVoice();
            row.setUserId(userId);
            row.setRequestId(safeRequestId);
            row.setDisplayName(safeDisplayName);
            row.setProviderSource("siliconflow");
            row.setModelName(safe(settings.modelName()));
            row.setConfigFingerprint(TtsVoiceProvisionService.buildRuntimeFingerprint(runtime, settings.modelName()));
            row.setVoiceUri("");
            row.setStatus(STATUS_PENDING);
            row.setLastError("");
            row.setDisabled(false);
            try {
                reservationService.reserve(userId, limit, row);
            } catch (DuplicateKeyException ex) {
                AppUserTtsVoice duplicate = voiceMapper.findByUserIdAndRequestId(userId, safeRequestId);
                if (duplicate != null) return toUserMap(duplicate, settings, null);
                throw ex;
            }

            row.setStatus(STATUS_PROVISIONING);
            voiceMapper.updateProvisionResult(row);
            try {
                TtsVoiceProvisionService.ProvisionedUserVoice result = provisionService.provisionUserVoice(
                        userId, row.getId(), safeSampleText, audio, mimeType, runtime);
                row.setVoiceUri(result.voiceUri());
                row.setModelName(result.modelName());
                row.setConfigFingerprint(result.configFingerprint());
                row.setStatus(STATUS_READY);
                row.setLastError("");
                if (voiceMapper.completeProvisioning(row) != 1) {
                    throw new BusinessException(ErrorCode.CONFLICT, "创建任务已结束，请重新创建");
                }
                return toUserMap(voiceMapper.findOwnedById(userId, row.getId()), settings, null);
            } catch (RuntimeException ex) {
                row.setVoiceUri("");
                row.setStatus(STATUS_FAILED);
                row.setLastError(trim(ex.getMessage(), 255));
                voiceMapper.failProvisioningById(row.getId(), row.getLastError());
                throw ex;
            }
        }
    }

    public Map<String, Object> providerStatus(long userId) {
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        TtsVoiceProvisionService.ProviderAccount account = provisionService.getProviderAccount(runtimeContext(settings));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("connected", true);
        data.put("providerSource", "siliconflow");
        data.put("modelName", safe(settings.modelName()));
        data.put("balance", safe(account.balance()));
        data.put("chargeBalance", safe(account.chargeBalance()));
        data.put("totalBalance", safe(account.totalBalance()));
        data.put("refreshedAt", LocalDateTime.now());
        return data;
    }

    public List<Map<String, Object>> providerVoices(long userId) {
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        List<TtsVoiceProvisionService.ProviderVoice> providerVoices =
                provisionService.listProviderVoices(runtimeContext(settings));
        Map<String, AppUserTtsVoice> importedByUri = new HashMap<>();
        for (AppUserTtsVoice voice : voiceMapper.listByUserId(userId)) {
            if (voice != null && StringUtils.hasText(voice.getVoiceUri())) {
                importedByUri.putIfAbsent(safe(voice.getVoiceUri()), voice);
            }
        }
        return providerVoices.stream().map(item -> {
            AppUserTtsVoice imported = importedByUri.get(safe(item.voiceUri()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("voiceUri", safe(item.voiceUri()));
            data.put("displayName", safe(item.displayName()));
            data.put("modelName", safe(item.modelName()));
            data.put("sampleText", safe(item.sampleText()));
            data.put("imported", imported != null);
            data.put("localVoiceId", imported == null || imported.getId() == null ? 0L : imported.getId());
            return data;
        }).toList();
    }

    public Map<String, Object> importProviderVoice(long userId, String requestId, String voiceUri) {
        String safeRequestId = normalizeRequestId(requestId);
        String safeVoiceUri = requiredText(voiceUri, 255, "请选择要导入的硅基流动音色");
        recoverStaleProvisioningForUser(userId);
        AppUserTtsVoice existingRequest = voiceMapper.findByUserIdAndRequestId(userId, safeRequestId);
        if (existingRequest != null) {
            if (existingRequest.getDeletedAt() == null) {
                return toUserMap(existingRequest, activeSettings(userId), null);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "本次导入标识已使用，请刷新后重试");
        }
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        if (!policy.isUserVoiceCreationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员暂未开放自建音色");
        }
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        TtsVoiceProvisionService.TtsRuntimeContext runtime = runtimeContext(settings);
        try (MediaConcurrencyGate.Lease ignored = mediaGate.acquire(
                MediaConcurrencyGate.Capability.VOICE_CLONE, userId, safeRequestId)) {
            TtsVoiceProvisionService.ProviderVoice providerVoice = provisionService.listProviderVoices(runtime).stream()
                    .filter(item -> safeVoiceUri.equals(safe(item.voiceUri())))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.NOT_FOUND, "该音色不在当前硅基流动账号中，请重新同步"));

            AppUserTtsVoice row = new AppUserTtsVoice();
            row.setUserId(userId);
            row.setRequestId(safeRequestId);
            row.setDisplayName(requiredText(providerVoice.displayName(), 64, "硅基流动音色名称无效"));
            row.setProviderSource("siliconflow");
            String modelName = StringUtils.hasText(providerVoice.modelName())
                    ? trim(providerVoice.modelName(), 255)
                    : safe(settings.modelName());
            row.setModelName(modelName);
            row.setConfigFingerprint(TtsVoiceProvisionService.buildRuntimeFingerprint(runtime, modelName));
            row.setVoiceUri(safeVoiceUri);
            row.setStatus(STATUS_READY);
            row.setLastError("");
            row.setDisabled(false);
            try {
                AppUserTtsVoice reserved = reservationService.reserveImported(
                        userId, limitForUser(policy, userId), row);
                return toUserMap(reserved, settings, null);
            } catch (DuplicateKeyException ex) {
                AppUserTtsVoice duplicate = voiceMapper.findByUserIdAndRequestId(userId, safeRequestId);
                if (duplicate != null && duplicate.getDeletedAt() == null) {
                    return toUserMap(duplicate, settings, null);
                }
                throw ex;
            }
        }
    }

    public void deleteProviderResource(long userId, long voiceId) {
        AppUserTtsVoice voice = requireOwned(userId, voiceId);
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        TtsVoiceProvisionService.TtsRuntimeContext runtime = runtimeContext(settings);
        RuntimeVoice resolved = resolveForRuntime(userId, voiceId, runtime);
        provisionService.deleteProviderVoice(runtime, resolved.voiceUri());
    }

    public Map<String, Object> rename(long userId, long voiceId, String displayName) {
        requireOwned(userId, voiceId);
        voiceMapper.updateDisplayName(userId, voiceId, requiredText(displayName, 64, "请填写音色名称"));
        return toUserMap(voiceMapper.findOwnedById(userId, voiceId), activeSettingsOrNull(userId), null);
    }

    @Transactional
    public void remove(long userId, long voiceId) {
        requireOwned(userId, voiceId);
        bindingMapper.deleteByVoiceId(voiceId);
        voiceMapper.softDelete(userId, voiceId);
    }

    @Transactional
    public Map<String, Object> saveBinding(
            long userId,
            String scopeType,
            long characterId,
            long memberId,
            Long voiceId
    ) {
        BindingScope scope = normalizeScope(scopeType, characterId, memberId);
        validateBindingTarget(userId, scope);
        if (voiceId == null || voiceId <= 0) {
            bindingMapper.deleteScope(userId, scope.type(), scope.characterId(), scope.memberId());
            return bindingMap(scope, null);
        }
        AppUserTtsVoice voice = requireOwned(userId, voiceId);
        if (!STATUS_READY.equalsIgnoreCase(safe(voice.getStatus())) || Boolean.TRUE.equals(voice.getDisabled())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前音色暂不可绑定");
        }
        H5UserAiProviderService.UserTtsSettings settings = activeSettings(userId);
        resolveForRuntime(userId, voiceId, runtimeContext(settings));
        AppUserTtsVoiceBinding row = new AppUserTtsVoiceBinding();
        row.setUserId(userId);
        row.setScopeType(scope.type());
        row.setCharacterId(scope.characterId());
        row.setMemberId(scope.memberId());
        row.setVoiceId(voiceId);
        if (bindingMapper.updateVoice(row) == 0) {
            try {
                bindingMapper.insert(row);
            } catch (DuplicateKeyException ex) {
                // Concurrent saves for the same scope converge on the latest requested voice.
                bindingMapper.updateVoice(row);
            }
        }
        return bindingMap(scope, voiceId);
    }

    public Map<String, Object> getBinding(long userId, String scopeType, long characterId, long memberId) {
        BindingScope scope = normalizeScope(scopeType, characterId, memberId);
        validateBindingTarget(userId, scope);
        return bindingMap(scope, bindingVoiceId(userId, scope.type(), scope.characterId(), scope.memberId()));
    }

    public Long resolveBoundVoiceId(long userId, long characterId, long memberId) {
        if (characterId > 0 && memberId > 0) {
            Long value = bindingVoiceId(userId, "MEMBER", characterId, memberId);
            if (value != null) return value;
        }
        if (characterId > 0) {
            Long value = bindingVoiceId(userId, "CHARACTER", characterId, 0);
            if (value != null) return value;
        }
        return bindingVoiceId(userId, "GLOBAL", 0, 0);
    }

    public Long resolveSpecificBoundVoiceId(long userId, long characterId, long memberId) {
        if (characterId > 0 && memberId > 0) {
            Long value = bindingVoiceId(userId, "MEMBER", characterId, memberId);
            if (value != null) return value;
        }
        if (characterId > 0) {
            return bindingVoiceId(userId, "CHARACTER", characterId, 0);
        }
        return null;
    }

    public Long resolveGlobalBoundVoiceId(long userId) {
        return bindingVoiceId(userId, "GLOBAL", 0, 0);
    }

    public RuntimeVoice resolveForRuntime(
            long userId,
            long voiceId,
            TtsVoiceProvisionService.TtsRuntimeContext runtime
    ) {
        AppUserTtsVoice voice = requireOwned(userId, voiceId);
        if (!STATUS_READY.equalsIgnoreCase(safe(voice.getStatus())) || Boolean.TRUE.equals(voice.getDisabled())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前自建音色暂不可用");
        }
        if (runtime == null || !runtime.customModeActive()
                || !"siliconflow".equalsIgnoreCase(safe(runtime.providerSource()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "自建音色只能使用创建时的硅基流动 BYOK 配置");
        }
        String fingerprint = TtsVoiceProvisionService.buildRuntimeFingerprint(runtime, voice.getModelName());
        if (!fingerprint.equals(safe(voice.getConfigFingerprint()))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "当前 API Key 或 TTS 配置已变化，请切回创建该音色时的配置");
        }
        return new RuntimeVoice(safe(voice.getVoiceUri()), safe(voice.getModelName()));
    }

    public Map<String, Object> listAdmin(String keyword, String status, int pageNum, int pageSize) {
        recoverAllStaleProvisioning();
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        String safeKeyword = trim(keyword, 80);
        String safeStatus = trim(status, 24).toUpperCase(Locale.ROOT);
        List<Map<String, Object>> rows = voiceMapper.listAdmin(
                safeKeyword.isBlank() ? null : safeKeyword,
                safeStatus.isBlank() ? null : safeStatus,
                (safePage - 1) * safeSize,
                safeSize
        ).stream().map(this::toAdminMap).toList();
        long total = voiceMapper.countAdmin(
                safeKeyword.isBlank() ? null : safeKeyword,
                safeStatus.isBlank() ? null : safeStatus);
        return Map.of("rows", rows, "total", total);
    }

    public void setAdminDisabled(long voiceId, boolean disabled) {
        AppUserTtsVoice voice = voiceMapper.findById(voiceId);
        if (voice == null || voice.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户音色不存在");
        }
        voiceMapper.updateDisabled(voiceId, disabled);
    }

    @Transactional
    public void finishAdminProvisioning(long voiceId) {
        AppUserTtsVoice voice = voiceMapper.findById(voiceId);
        if (voice == null || voice.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户音色不存在");
        }
        if (!isProvisioningStatus(voice.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有等待中或创建中的任务可以结束");
        }
        if (voiceMapper.failProvisioningById(voiceId, ADMIN_FINISHED_ERROR) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务状态已变化，请刷新后重试");
        }
    }

    @Scheduled(
            initialDelayString = "${app.user-tts-voice.stale-recovery-initial-delay-ms:60000}",
            fixedDelayString = "${app.user-tts-voice.stale-recovery-interval-ms:60000}"
    )
    @Transactional
    public void scheduledRecoverStaleProvisioning() {
        try {
            int recovered = recoverAllStaleProvisioning();
            if (recovered > 0) {
                log.info("recovered {} stale user TTS voice provisioning tasks", recovered);
            }
        } catch (RuntimeException ex) {
            log.error("failed to recover stale user TTS voice provisioning tasks", ex);
        }
    }

    int recoverAllStaleProvisioning() {
        return voiceMapper.failAllStaleProvisioning(staleProvisioningCutoff(), STALE_PROVISIONING_ERROR);
    }

    private int recoverStaleProvisioningForUser(long userId) {
        return voiceMapper.failStaleProvisioningByUserId(
                userId, staleProvisioningCutoff(), STALE_PROVISIONING_ERROR);
    }

    private static LocalDateTime staleProvisioningCutoff() {
        return LocalDateTime.now().minusMinutes(STALE_PROVISIONING_MINUTES);
    }

    private int limitForUser(EntitlementPolicy policy, long userId) {
        AppH5UserProfileExt ext = profileMapper.findByUserId(userId);
        int vipLevel = entitlementPolicyService.effectiveVipLevel(ext);
        return entitlementPolicyService.userVoiceLimitFor(policy, vipLevel);
    }

    private H5UserAiProviderService.UserTtsSettings activeSettings(long userId) {
        H5UserAiProviderService.UserTtsSettings settings = userAiProviderService.resolveActiveTtsSettingsForUser(userId);
        String reason = validateRuntimeReason(settings);
        if (!reason.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, reason);
        return settings;
    }

    private H5UserAiProviderService.UserTtsSettings activeSettingsOrNull(long userId) {
        try { return userAiProviderService.resolveActiveTtsSettingsForUser(userId); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String validateRuntimeReason(H5UserAiProviderService.UserTtsSettings settings) {
        if (settings == null) return "请先在 AI 设置中启用自己的 TTS API";
        if (!"siliconflow".equalsIgnoreCase(safe(settings.providerSource()))) return "第一期自建音色仅支持硅基流动";
        if (!StringUtils.hasText(settings.apiKey())) return "请先填写硅基流动 TTS API Key";
        if (!StringUtils.hasText(settings.modelName())) return "请先填写支持音色克隆的 TTS 模型";
        if (!StringUtils.hasText(settings.baseUrl())) return "当前硅基流动服务地址不可用";
        return "";
    }

    private static TtsVoiceProvisionService.TtsRuntimeContext runtimeContext(H5UserAiProviderService.UserTtsSettings settings) {
        return new TtsVoiceProvisionService.TtsRuntimeContext(
                true, settings.providerSource(), settings.baseUrl(), settings.apiKey(), settings.modelName());
    }

    private Map<String, Object> toUserMap(
            AppUserTtsVoice row,
            H5UserAiProviderService.UserTtsSettings settings,
            Long globalVoiceId
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (row == null) return data;
        String status = safe(row.getStatus()).toUpperCase(Locale.ROOT);
        boolean configMatches = false;
        if (STATUS_READY.equals(status) && settings != null && "siliconflow".equalsIgnoreCase(safe(settings.providerSource()))) {
            TtsVoiceProvisionService.TtsRuntimeContext runtime = runtimeContext(settings);
            configMatches = safe(row.getConfigFingerprint()).equals(
                    TtsVoiceProvisionService.buildRuntimeFingerprint(runtime, row.getModelName()));
        }
        data.put("id", row.getId());
        data.put("displayName", safe(row.getDisplayName()));
        data.put("providerSource", safe(row.getProviderSource()));
        data.put("modelName", safe(row.getModelName()));
        data.put("status", status);
        data.put("statusText", userStatusText(row, configMatches));
        data.put("available", STATUS_READY.equals(status) && configMatches && !Boolean.TRUE.equals(row.getDisabled()));
        data.put("configMatches", configMatches);
        data.put("disabled", Boolean.TRUE.equals(row.getDisabled()));
        data.put("isDefault", globalVoiceId != null && globalVoiceId.equals(row.getId()));
        data.put("lastError", safe(row.getLastError()));
        data.put("createdAt", row.getCreatedAt());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private static String userStatusText(AppUserTtsVoice row, boolean configMatches) {
        if (Boolean.TRUE.equals(row.getDisabled())) return "已被管理员停用";
        String status = safe(row.getStatus()).toUpperCase(Locale.ROOT);
        if (STATUS_READY.equals(status)) return configMatches ? "可使用" : "当前 API 配置不匹配";
        if (STATUS_FAILED.equals(status)) return StringUtils.hasText(row.getLastError()) ? row.getLastError() : "创建失败";
        if (STATUS_PROVISIONING.equals(status)) return "正在创建";
        return "等待创建";
    }

    private Map<String, Object> toAdminMap(AppUserTtsVoice row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("userId", row.getUserId());
        data.put("displayName", safe(row.getDisplayName()));
        data.put("providerSource", safe(row.getProviderSource()));
        data.put("modelName", safe(row.getModelName()));
        data.put("status", safe(row.getStatus()));
        data.put("lastError", safe(row.getLastError()));
        data.put("disabled", Boolean.TRUE.equals(row.getDisabled()));
        data.put("createdAt", row.getCreatedAt());
        data.put("updatedAt", row.getUpdatedAt());
        return data;
    }

    private AppUserTtsVoice requireOwned(long userId, long voiceId) {
        AppUserTtsVoice row = voiceMapper.findOwnedById(userId, voiceId);
        if (row == null) throw new BusinessException(ErrorCode.NOT_FOUND, "音色不存在或无权访问");
        return row;
    }

    private Long bindingVoiceId(long userId, String scope, long characterId, long memberId) {
        AppUserTtsVoiceBinding row = bindingMapper.find(userId, scope, characterId, memberId);
        return row == null ? null : row.getVoiceId();
    }

    private void validateBindingTarget(long userId, BindingScope scope) {
        if ("GLOBAL".equals(scope.type())) return;
        entitlementService.requireCharacterVisibleToUser(scope.characterId(), userId);
        if (!"MEMBER".equals(scope.type())) return;
        List<AppCharacterMember> members = characterStudioMapper.listMembers(scope.characterId());
        boolean belongsToCharacter = members != null && members.stream()
                .anyMatch(member -> member != null && member.getId() != null
                        && member.getId().longValue() == scope.memberId());
        if (!belongsToCharacter) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色成员不存在或不属于当前角色");
        }
    }

    private static boolean isProvisioningStatus(String status) {
        String normalized = safe(status).toUpperCase(Locale.ROOT);
        return STATUS_PENDING.equals(normalized) || STATUS_PROVISIONING.equals(normalized);
    }

    private static BindingScope normalizeScope(String scopeType, long characterId, long memberId) {
        String scope = safe(scopeType).toUpperCase(Locale.ROOT);
        if ("GLOBAL".equals(scope)) return new BindingScope(scope, 0, 0);
        if ("CHARACTER".equals(scope) && characterId > 0) return new BindingScope(scope, characterId, 0);
        if ("MEMBER".equals(scope) && characterId > 0 && memberId > 0) return new BindingScope(scope, characterId, memberId);
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "音色绑定范围无效");
    }

    private static Map<String, Object> bindingMap(BindingScope scope, Long voiceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scopeType", scope.type());
        data.put("characterId", scope.characterId());
        data.put("memberId", scope.memberId());
        data.put("voiceId", voiceId == null ? 0L : voiceId);
        return data;
    }

    private static byte[] readAndValidateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择参考音频");
        if (file.getSize() <= 0 || file.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频不能超过 15MB");
        }
        try { return file.getBytes(); }
        catch (Exception ex) { throw new BusinessException(ErrorCode.VALIDATION_FAILED, "参考音频读取失败"); }
    }

    private static String normalizeRequestId(String value) {
        String text = trim(value, 64);
        if (!text.matches("[A-Za-z0-9_-]{12,64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "音色创建请求标识无效");
        }
        return text;
    }

    private static String requiredText(String value, int max, String message) {
        String text = trim(value, max);
        if (!StringUtils.hasText(text)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        return text;
    }

    private static String trim(String value, int max) {
        String text = safe(value).trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private record BindingScope(String type, long characterId, long memberId) {}
}
