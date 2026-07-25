package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.entity.AppCharacterSystemPromotion;
import com.example.sillyspringboot.admin.mapper.AppCharacterSystemPromotionMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.entity.AppCharacterOpening;
import com.example.sillyspringboot.character.entity.AppCharacterOpeningSegment;
import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CharacterSystemPromotionService {
    private static final Logger log = LoggerFactory.getLogger(CharacterSystemPromotionService.class);

    private final AppFeatureSettingsService featureSettingsService;
    private final AppCharacterMapper characterMapper;
    private final CharacterStudioMapper studioMapper;
    private final AppLorebookEntryMapper lorebookMapper;
    private final AppCharacterSystemPromotionMapper promotionMapper;
    private final StAdapter stAdapter;

    public CharacterSystemPromotionService(
            AppFeatureSettingsService featureSettingsService,
            AppCharacterMapper characterMapper,
            CharacterStudioMapper studioMapper,
            AppLorebookEntryMapper lorebookMapper,
            AppCharacterSystemPromotionMapper promotionMapper,
            StAdapter stAdapter
    ) {
        this.featureSettingsService = featureSettingsService;
        this.characterMapper = characterMapper;
        this.studioMapper = studioMapper;
        this.lorebookMapper = lorebookMapper;
        this.promotionMapper = promotionMapper;
        this.stAdapter = stAdapter;
    }

    @Transactional
    public PromotionResult promote(long sourceCharacterId, boolean keepCreatorAttribution, String operator) {
        if (!featureSettingsService.getSettings().isUserCharacterPromotionEnabled()) {
            throw new IllegalStateException("用户角色复制为系统角色的功能开关尚未开启");
        }
        AppCharacter source = characterMapper.findById(sourceCharacterId);
        if (source == null || source.getDeletedAt() != null) {
            throw new IllegalArgumentException("来源角色不存在");
        }
        if (!Boolean.TRUE.equals(source.getPrivateCard()) || source.getOwnerUserId() == null) {
            throw new IllegalArgumentException("仅用户创建的角色卡支持复制为系统角色");
        }
        AppCharacterSystemPromotion existing = promotionMapper.findBySourceCharacterId(sourceCharacterId);
        if (existing != null) {
            throw new IllegalStateException("该用户角色已经复制过，系统角色 ID：" + existing.getTargetCharacterId());
        }

        String sourceStAvatar = trimToEmpty(source.getStAvatarUrl());
        if (sourceStAvatar.isBlank()) {
            throw new IllegalStateException("来源角色缺少 ST 文件，无法执行完整复制");
        }
        byte[] exportedPng = stAdapter.exportCharacterPng(sourceStAvatar);
        if (exportedPng == null || exportedPng.length == 0) {
            throw new IllegalStateException("来源角色的 ST PNG 导出为空，复制已中止");
        }

        String preferredStAvatar = "system_copy_" + sourceCharacterId + "_" + UUID.randomUUID() + ".png";
        Object importResult = stAdapter.importCharacterPng(
                exportedPng,
                sourceStAvatar,
                new StCharacterImportRequest("png", preferredStAvatar)
        );
        String copiedStAvatar = importedAvatarUrl(importResult);
        if (copiedStAvatar.isBlank()) {
            throw new IllegalStateException("ST 未返回新角色文件名，复制已中止");
        }
        boolean cleanupRegistered = registerRollbackCleanup(copiedStAvatar);

        try {
            AppCharacter target = copyBaseCharacter(source, copiedStAvatar, keepCreatorAttribution);
            characterMapper.insertFull(target);
            copyStudio(sourceCharacterId, target.getId());

            AppCharacterSystemPromotion audit = new AppCharacterSystemPromotion();
            audit.setSourceCharacterId(sourceCharacterId);
            audit.setSourceUserId(source.getOwnerUserId());
            audit.setTargetCharacterId(target.getId());
            audit.setKeepCreatorAttribution(keepCreatorAttribution);
            audit.setPromotedBy(normalizeOperator(operator));
            promotionMapper.insert(audit);
            return new PromotionResult(target.getId(), copiedStAvatar);
        } catch (RuntimeException ex) {
            if (!cleanupRegistered) {
                deleteCopiedStFileQuietly(copiedStAvatar);
            }
            throw ex;
        }
    }

    private void copyStudio(long sourceCharacterId, long targetCharacterId) {
        String cardType = studioMapper.findCardType(sourceCharacterId);
        studioMapper.updateCardType(targetCharacterId, "ENSEMBLE".equalsIgnoreCase(cardType) ? "ENSEMBLE" : "SINGLE");

        Map<Long, Long> memberIds = new HashMap<>();
        for (AppCharacterMember source : studioMapper.listMembers(sourceCharacterId)) {
            AppCharacterMember target = new AppCharacterMember();
            target.setCharacterId(targetCharacterId);
            target.setName(source.getName());
            target.setTagline(source.getTagline());
            target.setPersona(source.getPersona());
            target.setAvatarUrl(source.getAvatarUrl());
            target.setVoiceConfigJson(null);
            target.setImageReferenceUrl(source.getImageReferenceUrl());
            target.setPrimaryMember(source.getPrimaryMember());
            target.setSortOrder(source.getSortOrder());
            target.setEnabled(source.getEnabled());
            studioMapper.insertMember(target);
            memberIds.put(source.getId(), target.getId());
        }

        Map<Long, Long> openingIds = new HashMap<>();
        for (AppCharacterOpening source : studioMapper.listOpenings(sourceCharacterId)) {
            AppCharacterOpening target = new AppCharacterOpening();
            target.setCharacterId(targetCharacterId);
            target.setTitle(source.getTitle());
            target.setSummary(source.getSummary());
            target.setScenarioOverride(source.getScenarioOverride());
            target.setDefaultOpening(source.getDefaultOpening());
            target.setSortOrder(source.getSortOrder());
            target.setEnabled(source.getEnabled());
            studioMapper.insertOpening(target);
            openingIds.put(source.getId(), target.getId());
        }

        for (AppCharacterOpeningSegment source : studioMapper.listSegmentsByCharacter(sourceCharacterId)) {
            Long targetOpeningId = openingIds.get(source.getOpeningId());
            if (targetOpeningId == null) {
                continue;
            }
            AppCharacterOpeningSegment target = new AppCharacterOpeningSegment();
            target.setOpeningId(targetOpeningId);
            target.setSpeakerMemberId(source.getSpeakerMemberId() == null ? null : memberIds.get(source.getSpeakerMemberId()));
            target.setSpeakerType(source.getSpeakerType());
            target.setContent(source.getContent());
            target.setSortOrder(source.getSortOrder());
            studioMapper.insertOpeningSegment(target);
        }

        for (AppLorebookEntry source : lorebookMapper.listAllByCharacterId(sourceCharacterId)) {
            AppLorebookEntry target = new AppLorebookEntry();
            target.setCharacterId(targetCharacterId);
            target.setTitle(source.getTitle());
            target.setMemberId(source.getMemberId() == null ? null : memberIds.get(source.getMemberId()));
            target.setKeywordsCsv(source.getKeywordsCsv());
            target.setSecondaryKeywordsCsv(source.getSecondaryKeywordsCsv());
            target.setMatchMode(source.getMatchMode());
            target.setContent(source.getContent());
            target.setPriority(source.getPriority());
            target.setConstantInjection(source.getConstantInjection());
            target.setScanDepth(source.getScanDepth());
            target.setInjectionPosition(source.getInjectionPosition());
            target.setEnabled(source.getEnabled());
            target.setSource(source.getSource());
            target.setRawEntryJson(source.getRawEntryJson());
            lorebookMapper.insert(target);
        }
    }

    private static AppCharacter copyBaseCharacter(
            AppCharacter source,
            String copiedStAvatar,
            boolean keepCreatorAttribution
    ) {
        AppCharacter target = new AppCharacter();
        target.setStAvatarUrl(copiedStAvatar);
        target.setAvatarUrl(source.getAvatarUrl());
        target.setCoverUrl(source.getCoverUrl());
        target.setChatBackgroundUrl(source.getChatBackgroundUrl());
        target.setStWorldNamesJson(source.getStWorldNamesJson());
        target.setOwnerUserId(null);
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setTagline(source.getTagline());
        target.setBio(source.getBio());
        target.setPublicSummary(source.getPublicSummary());
        target.setPublicTagsJson(source.getPublicTagsJson());
        target.setPublicWarningsJson(source.getPublicWarningsJson());
        target.setHealthScore(source.getHealthScore());
        target.setHealthIssuesJson(source.getHealthIssuesJson());
        target.setPersona(source.getPersona());
        target.setScenario(source.getScenario());
        target.setFirstMessage(source.getFirstMessage());
        target.setAlternateGreetingsJson(source.getAlternateGreetingsJson());
        target.setMesExample(source.getMesExample());
        target.setSystemPrompt(source.getSystemPrompt());
        target.setPostHistoryInstructions(source.getPostHistoryInstructions());
        target.setPrivateCard(Boolean.FALSE);
        target.setReviewStatus(CharacterReviewStatus.APPROVED);
        target.setReviewReason(null);
        target.setReviewedAt(null);
        target.setReviewedBy(null);
        target.setOccupationLabel(source.getOccupationLabel());
        target.setTagsJson(source.getTagsJson());
        target.setVipOnly(source.getVipOnly());
        target.setUnlockedDefault(source.getUnlockedDefault());
        target.setClientVisible(Boolean.FALSE);
        target.setPreviewBlurVipLevel(source.getPreviewBlurVipLevel());
        target.setLikeCount(0);
        target.setDislikeCount(0);
        target.setCreatorName(keepCreatorAttribution ? source.getCreatorName() : null);
        target.setCreatorHandle(keepCreatorAttribution ? source.getCreatorHandle() : null);
        target.setTokenDisplay(source.getTokenDisplay());
        target.setGameplayType(source.getGameplayType());
        target.setChatModesJson(source.getChatModesJson());
        target.setSortOrder(0);
        target.setCreatorNotes(source.getCreatorNotes());
        target.setStExtraJson(source.getStExtraJson());
        return target;
    }

    private boolean registerRollbackCleanup(String stAvatarUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteCopiedStFileQuietly(stAvatarUrl);
                }
            }
        });
        return true;
    }

    private void deleteCopiedStFileQuietly(String stAvatarUrl) {
        try {
            stAdapter.deleteCharacter(stAvatarUrl, false);
        } catch (Exception cleanupError) {
            log.error("Failed to clean copied ST character after promotion rollback: {}", stAvatarUrl, cleanupError);
        }
    }

    private static String normalizeOperator(String operator) {
        String value = trimToEmpty(operator);
        if (value.isBlank()) {
            return "admin";
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String importedAvatarUrl(Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return "";
        }
        Object raw = map.get("file_name");
        String value = trimToEmpty(raw == null ? null : String.valueOf(raw));
        if (value.isBlank()) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT).endsWith(".png") ? value : value + ".png";
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public record PromotionResult(long targetCharacterId, String stAvatarUrl) {
    }
}
