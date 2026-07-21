package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.config.AppProperties;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import com.example.sillyspringboot.conversation.dto.ConversationStBindingDto;
import com.example.sillyspringboot.conversation.dto.ConversationSummaryDto;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.entity.AppConversationIdempotency;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationIdempotencyMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppConversationService {

    private final AppConversationMapper conversationMapper;
    private final AppConversationStBindingMapper bindingMapper;
    private final AppConversationIdempotencyMapper idempotencyMapper;
    private final AppCharacterMapper characterMapper;
    private final AppTokenService tokenService;
    private final AppProperties appProperties;
    private final AppH5ProfileMapper h5ProfileMapper;
    private final StWorldbookCatalogService worldbookCatalogService;
    private final H5VisitorTrialGuardService visitorTrialGuardService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AppConversationService(
            AppConversationMapper conversationMapper,
            AppConversationStBindingMapper bindingMapper,
            AppConversationIdempotencyMapper idempotencyMapper,
            AppCharacterMapper characterMapper,
            AppTokenService tokenService,
            AppProperties appProperties,
            AppH5ProfileMapper h5ProfileMapper,
            StWorldbookCatalogService worldbookCatalogService,
            H5VisitorTrialGuardService visitorTrialGuardService
    ) {
        this.conversationMapper = conversationMapper;
        this.bindingMapper = bindingMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.characterMapper = characterMapper;
        this.tokenService = tokenService;
        this.appProperties = appProperties;
        this.h5ProfileMapper = h5ProfileMapper;
        this.worldbookCatalogService = worldbookCatalogService;
        this.visitorTrialGuardService = visitorTrialGuardService;
    }

    @Transactional
    public ConversationDetailDto createOrEnsure(CreateConversationRequest request, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        long characterId = request.getCharacterId();
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "idempotencyKey 缺失");
        }

        // 幂等：如果同一 user + idempotencyKey 已创建过会话，则返回同一个 conversationId。
        // 如果请求里的 characterId 与已存在会话不一致，则返回 CONFLICT，避免自相矛盾数据。
        AppConversationIdempotency existedIdem = idempotencyMapper.findByUserAndKey(userId, idempotencyKey);
        if (existedIdem != null) {
            long conversationId = existedIdem.getConversationId();
            AppConversation existed = conversationMapper.findByIdForUser(conversationId, userId);
            if (existed == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
            }
            long persistedCharacterId = existed.getCharacterId();
            if (persistedCharacterId != characterId) {
                throw new BusinessException(ErrorCode.CONFLICT, "会话角色不一致");
            }
            AppCharacter ch0 = characterMapper.findById(persistedCharacterId);
            assertH5CharacterVisibleToUser(ch0, userId);
            ensureBinding(existed, conversationId, userId, persistedCharacterId, ch0 == null ? null : ch0.getStAvatarUrl());
            AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
            return toDetailDto(conversationId, persistedCharacterId, existed.getTitle(), userId, binding);
        }

        // 角色必须存在（阶段 6：characterId 统一为业务目录 id）
        AppCharacter ch = characterMapper.findById(characterId);
        if (ch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }

        // 1) 创建会话（数据库主键由服务端生成，客户端不可控）
        assertH5CharacterVisibleToUser(ch, userId);

        AppConversation c = new AppConversation();
        c.setUserId(userId);
        c.setCharacterId(characterId);
        c.setTitle(null);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(c);

        Long conversationId = c.getId();
        if (conversationId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "会话创建失败");
        }

        // 2) 绑定 ST 资源（本阶段只落映射，不调用 ST）
        ensureBinding(c, conversationId, userId, characterId, ch.getStAvatarUrl());

        // 3) 写入幂等映射；若并发下重复插入，回退为“重新查询映射再返回”
        try {
            AppConversationIdempotency idem = new AppConversationIdempotency();
            idem.setUserId(userId);
            idem.setIdempotencyKey(idempotencyKey);
            idem.setConversationId(conversationId);
            idem.setCreatedAt(LocalDateTime.now());
            idempotencyMapper.insert(idem);
        } catch (Exception e) {
            // 唯一约束冲突：重新查询幂等映射返回结果
            AppConversationIdempotency reloaded = idempotencyMapper.findByUserAndKey(userId, idempotencyKey);
            if (reloaded != null) {
                AppConversation persisted = conversationMapper.findByIdForUser(reloaded.getConversationId(), userId);
                if (persisted == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "幂等回退失败");
                }
                if (persisted.getCharacterId() != characterId) {
                    throw new BusinessException(ErrorCode.CONFLICT, "会话角色不一致");
                }
                AppCharacter ch0 = characterMapper.findById(persisted.getCharacterId());
                assertH5CharacterVisibleToUser(ch0, userId);
                ensureBinding(persisted, persisted.getId(), userId, persisted.getCharacterId(), ch0 == null ? null : ch0.getStAvatarUrl());
                AppConversationStBinding binding = bindingMapper.findByConversationId(persisted.getId());
                return toDetailDto(persisted.getId(), persisted.getCharacterId(), persisted.getTitle(), userId, binding);
            }
            throw e;
        }

        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        return toDetailDto(conversationId, characterId, c.getTitle(), userId, binding);
    }

    public List<ConversationSummaryDto> listForUser(String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        List<AppConversation> list = conversationMapper.listByUser(userId);

        List<ConversationSummaryDto> result = new ArrayList<>();
        for (AppConversation c : list) {
            result.add(new ConversationSummaryDto(
                    c.getId(),
                    c.getCharacterId(),
                    c.getTitle(),
                    c.getCreatedAt(),
                    c.getUpdatedAt()
            ));
        }
        return result;
    }

    public List<ConversationInboxItemDto> listInboxForUser(String token, int limit) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        int lim = Math.max(1, Math.min(200, limit));
        return conversationMapper.listInboxByUserExcludeArchived(userId, lim);
    }

    public ConversationDetailDto getDetail(long conversationId, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        AppCharacter ch = characterMapper.findById(c.getCharacterId());
        assertH5CharacterVisibleToUser(ch, userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        return toDetailDto(conversationId, c.getCharacterId(), c.getTitle(), userId, binding);
    }

    public ConversationDetailDto findDetailByH5Character(String clientUid, long characterId, String token) {
        if (characterId <= 0) {
            return null;
        }
        return findDetailByIdempotencyKey(buildH5IdempotencyKey(clientUid, characterId), token);
    }

    public Long findConversationIdByH5CharacterForSessionCleanup(String clientUid, long characterId, String token) {
        if (characterId <= 0) {
            return null;
        }
        long userId = tokenService.validateAndLoadUser(token).getId();
        String idempotencyKey = buildH5IdempotencyKey(clientUid, characterId);
        AppConversationIdempotency existedIdem = idempotencyMapper.findByUserAndKey(userId, idempotencyKey);
        if (existedIdem == null || existedIdem.getConversationId() == null) {
            return null;
        }
        AppConversation existed = conversationMapper.findByIdForUser(existedIdem.getConversationId(), userId);
        if (existed == null || existed.getId() == null) {
            return null;
        }
        Long existedCharacterId = existed.getCharacterId();
        if (existedCharacterId == null || existedCharacterId.longValue() != characterId) {
            return null;
        }
        return existed.getId();
    }

    public ConversationDetailDto ensureDetailByH5Character(String clientUid, long characterId, String token) {
        if (characterId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId invalid");
        }
        return createOrEnsureForH5(clientUid, characterId, token);
    }

    public ConversationDetailDto createOrEnsureForH5(String clientUid, long characterId, String token) {
        if (characterId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId invalid");
        }
        String idempotencyKey = buildH5IdempotencyKey(clientUid, characterId);
        ConversationDetailDto existed = findDetailByIdempotencyKey(idempotencyKey, token);
        if (existed != null) {
            return existed;
        }
        visitorTrialGuardService.guardAnonymousConversationCreation(clientUid, token, idempotencyKey);
        CreateConversationRequest request = new CreateConversationRequest();
        request.setCharacterId(characterId);
        request.setIdempotencyKey(idempotencyKey);
        return createOrEnsure(request, token);
    }

    @Transactional
    public void updateWorldbooks(long conversationId, List<String> worldNames, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话绑定不存在");
        }

        List<String> normalized = worldbookCatalogService.normalizeAndValidateWorldNames(worldNames);
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "worldNames 不合法");
        }
        bindingMapper.updateWorldNamesJson(conversationId, json);
    }

    /**
     * 后台用：直接更新会话绑定的世界书（不依赖 app user token）。
     */
    @Transactional
    public void updateWorldbooksAdmin(long conversationId, List<String> worldNames) {
        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId 不合法");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话绑定不存在");
        }

        List<String> normalized = worldbookCatalogService.normalizeAndValidateWorldNames(worldNames);
        String json;
        try {
            json = objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "worldNames 不合法");
        }
        bindingMapper.updateWorldNamesJson(conversationId, json);
    }

    /*
    @Transactional
    public void updateStDisplayNameOverride(long conversationId, String stDisplayNameOverride, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "浼氳瘽涓嶅瓨鍦?);
        }
        AppCharacter ch = characterMapper.findById(c.getCharacterId());
        assertH5CharacterVisibleToUser(ch, userId);
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "浼氳瘽缁戝畾涓嶅瓨鍦?);
        }
        bindingMapper.updateStDisplayNameOverride(conversationId, normalizeStDisplayName(stDisplayNameOverride));
    }

    @Transactional
    public void updateStDisplayNameOverrideAdmin(long conversationId, String stDisplayNameOverride) {
        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId 涓嶅悎娉?);
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "浼氳瘽缁戝畾涓嶅瓨鍦?);
        }
        bindingMapper.updateStDisplayNameOverride(conversationId, normalizeStDisplayName(stDisplayNameOverride));
    }

    */

    @Transactional
    public void updateStDisplayNameOverride(long conversationId, String stDisplayNameOverride, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppConversation c = conversationMapper.findByIdForUser(conversationId, userId);
        if (c == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation not found");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation binding not found");
        }
        bindingMapper.updateStDisplayNameOverride(conversationId, normalizeStDisplayName(stDisplayNameOverride));
    }

    @Transactional
    public void updateStDisplayNameOverrideAdmin(long conversationId, String stDisplayNameOverride) {
        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId invalid");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation binding not found");
        }
        bindingMapper.updateStDisplayNameOverride(conversationId, normalizeStDisplayName(stDisplayNameOverride));
    }

    public String resolveEffectiveStDisplayName(long userId, AppConversationStBinding binding) {
        String override = normalizeStDisplayName(binding == null ? null : binding.getStDisplayNameOverride());
        if (override != null) {
            return override;
        }
        AppH5Profile profile = h5ProfileMapper == null ? null : h5ProfileMapper.findByUserId(userId);
        String stDisplayName = normalizeStDisplayName(profile == null ? null : profile.getStDisplayName());
        if (stDisplayName != null) {
            return stDisplayName;
        }
        if (profile != null && profile.getDisplayName() != null && !profile.getDisplayName().trim().isBlank()) {
            return trimToMax(profile.getDisplayName(), 64);
        }
        return null;
    }

    public static String buildH5IdempotencyKey(String clientUid, long characterId) {
        String safeClientUid = clientUid == null ? "" : clientUid.trim();
        if (safeClientUid.isBlank() || characterId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid/characterId missing");
        }
        return "h5:" + safeClientUid + ":" + characterId;
    }

    private void assertH5CharacterVisibleToUser(AppCharacter character, long userId) {
        if (character == null || character.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
        Long ownerId = character.getOwnerUserId();
        if (ownerId != null || Boolean.TRUE.equals(character.getPrivateCard())) {
            if (ownerId == null || ownerId.longValue() != userId) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
            }
            return;
        }
        if (Boolean.FALSE.equals(character.getClientVisible())
                || !CharacterReviewStatus.APPROVED.equals(CharacterReviewStatus.normalize(character.getReviewStatus()))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "character not found");
        }
    }

    private void ensureBinding(AppConversation c, Long conversationId, long userId, long characterId, String stAvatarUrl) {
        AppConversationStBinding existed = bindingMapper.findByConversationId(conversationId);
        if (existed != null) return;

        // 命名规则冻结（不随机、可追踪，方便审计与运维排查）
        String env = appProperties.getEnvironment();
        String stChatRef = "app_" + env + "_" + conversationId;
        String stCharacterRef = "stchar_" + characterId + "_v1";
        String stRuntimeProfile = "default";

        AppConversationStBinding b = new AppConversationStBinding();
        b.setUserId(userId);
        b.setCharacterId(characterId);
        b.setConversationId(conversationId);
        b.setStRuntimeProfile(stRuntimeProfile);
        b.setStCharacterRef(stCharacterRef);
        b.setStChatRef(stChatRef);
        // 运营期：ST chats API 需要 avatar_url/file_name；此处先按可配置默认 avatar_url + 稳定 file_name 规则落库
        // avatar_url 对应 ST 的角色头像文件名；后续接通“角色资产就绪”后可改为真实角色头像
        b.setStAvatarUrl(stAvatarUrl);
        b.setStChatFileName(stChatRef);
        b.setStatus("CREATED");
        bindingMapper.insert(b);
    }

    public static String normalizeStDisplayName(String raw) {
        String text = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').trim();
        if (text.isBlank()) {
            return null;
        }
        return trimToMax(text, 64);
    }

    private static String trimToMax(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength).trim();
    }

    private List<String> parseWorldNamesJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = objectMapper.readValue(raw, java.util.List.class);
            if (list == null) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>();
            for (Object item : list) {
                String text = trimToMax(item == null ? "" : String.valueOf(item), 128);
                if (text != null && !text.isBlank()) {
                    normalized.add(text);
                }
            }
            return normalized.stream().distinct().limit(10).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private ConversationDetailDto findDetailByIdempotencyKey(String idempotencyKey, String token) {
        long userId = tokenService.validateAndLoadUser(token).getId();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        AppConversationIdempotency existedIdem = idempotencyMapper.findByUserAndKey(userId, idempotencyKey);
        if (existedIdem == null || existedIdem.getConversationId() == null) {
            return null;
        }
        AppConversation existed = conversationMapper.findByIdForUser(existedIdem.getConversationId(), userId);
        if (existed == null) {
            return null;
        }
        AppCharacter ch = characterMapper.findById(existed.getCharacterId());
        assertH5CharacterVisibleToUser(ch, userId);
        ensureBinding(existed, existed.getId(), userId, existed.getCharacterId(), ch == null ? null : ch.getStAvatarUrl());
        AppConversationStBinding binding = bindingMapper.findByConversationId(existed.getId());
        return toDetailDto(existed.getId(), existed.getCharacterId(), existed.getTitle(), userId, binding);
    }

    private ConversationDetailDto toDetailDto(
            long conversationId,
            long characterId,
            String title,
            long userId,
            AppConversationStBinding binding
    ) {
        ConversationStBindingDto stDto = binding == null ? null : new ConversationStBindingDto(
                binding.getStRuntimeProfile(),
                binding.getStCharacterRef(),
                binding.getStChatRef(),
                binding.getStAvatarUrl(),
                binding.getStChatFileName(),
                parseWorldNamesJson(binding.getStWorldNamesJson()),
                normalizeStDisplayName(binding.getStDisplayNameOverride()),
                binding.getStatus()
        );
        return new ConversationDetailDto(conversationId, characterId, title, stDto);
    }
}
