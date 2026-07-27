package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.dto.AppCharacterSummaryDto;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段 6：角色目录（业务侧 id ↔ ST avatar_url 映射）。
 */
@Service
public class CharacterCatalogService {

    private static final Logger log = LoggerFactory.getLogger(CharacterCatalogService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int ST_UPSERT_CAP = 800;
    private static final int MAX_ST_AVATAR_URL = 255;
    private static final int MAX_NAME = 255;
    private static final int MAX_TAGLINE = 255;
    private static final int MAX_CREATOR_NAME = 255;
    private static final int SAFE_TEXT_COLUMN_LIMIT = 12000;
    private static final long FEED_SYNC_SUCCESS_COOLDOWN_MS = 5_000L;
    private static final long FEED_SYNC_FAILURE_COOLDOWN_MS = 15_000L;

    private final AppCharacterMapper mapper;
    private final StAdapter stAdapter;
    private final StWorldbookCatalogService worldbookCatalogService;
    private final EmbeddedLorebookSyncService embeddedLorebookSyncService;
    private final CharacterPublicProfileService publicProfileService;
    private final Object feedSyncLock = new Object();
    private volatile long lastFeedSyncAttemptAt = 0L;
    private volatile boolean lastFeedSyncSucceeded = false;

    public CharacterCatalogService(
            AppCharacterMapper mapper,
            StAdapter stAdapter,
            StWorldbookCatalogService worldbookCatalogService,
            EmbeddedLorebookSyncService embeddedLorebookSyncService,
            CharacterPublicProfileService publicProfileService
    ) {
        this.mapper = mapper;
        this.stAdapter = stAdapter;
        this.worldbookCatalogService = worldbookCatalogService;
        this.embeddedLorebookSyncService = embeddedLorebookSyncService;
        this.publicProfileService = publicProfileService;
    }

    @Transactional
    public List<AppCharacterSummaryDto> syncFeed(int limit) {
        int lim = Math.max(1, Math.min(500, limit));
        refreshFeedFromStIfDue();
        return listFromDbOnly(lim);
    }

    @Transactional
    public void refreshFeedFromStNow() {
        refreshFeedFromSt(true);
    }

    private void refreshFeedFromStIfDue() {
        refreshFeedFromSt(false);
    }

    private void refreshFeedFromSt(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && !shouldAttemptFeedSync(now)) {
            return;
        }
        synchronized (feedSyncLock) {
            long refreshedNow = System.currentTimeMillis();
            if (!force && !shouldAttemptFeedSync(refreshedNow)) {
                return;
            }
            boolean success = false;
            try {
                syncFeedFromSt();
                success = true;
            } catch (StUnavailableException e) {
                log.warn(
                    "ST 不可用，发现页回退公开目录 app_character（请启动 ST 并检查 sillytavern.base-url；聊天仍依赖 ST）：{}",
                    e.getMessage(),
                    e);
            } catch (Exception e) {
                log.warn(
                        "ST feed sync failed unexpectedly, fallback to app_character only: {}",
                        e.getMessage(),
                        e
                );
            } finally {
                lastFeedSyncAttemptAt = System.currentTimeMillis();
                lastFeedSyncSucceeded = success;
            }
        }
    }

    private boolean shouldAttemptFeedSync(long now) {
        long lastAttempt = lastFeedSyncAttemptAt;
        if (lastAttempt <= 0L) {
            return true;
        }
        long cooldown = lastFeedSyncSucceeded ? FEED_SYNC_SUCCESS_COOLDOWN_MS : FEED_SYNC_FAILURE_COOLDOWN_MS;
        return now - lastAttempt >= cooldown;
    }

    private void syncFeedFromSt() {
        List<StCharacterSummary> st = stAdapter.listCharactersAll();
        int cap = 0;
        for (StCharacterSummary s : st) {
            if (cap >= ST_UPSERT_CAP) {
                break;
            }
            if (s == null || s.name() == null || s.name().isBlank()) {
                continue;
            }
            String av = normalizeStAvatarUrl(s.avatar());
            if (av.isBlank()) {
                continue;
            }
            refreshExistingSystemByAvatarUrl(av, s.name(), s.description());
            cap++;
        }
    }

    private List<AppCharacterSummaryDto> listFromDbOnly(int limit) {
        List<AppCharacter> rows = mapper.listPublicDiscover(limit);
        List<AppCharacterSummaryDto> out = new ArrayList<>();
        for (AppCharacter c : rows) {
            if (c == null) {
                continue;
            }
            if (isBlank(c.getPublicSummary()) || c.getHealthScore() == null) {
                applyPublicProfile(c);
                mapper.updateById(c);
            }
            AppCharacterSummaryDto dto = AppCharacterSummaryDto.from(c);
            if (dto != null) {
                out.add(dto);
            }
        }
        return out;
    }

    @Transactional
    public AppCharacter ensurePublicCharacter(long characterId) {
        AppCharacter c = mapper.findPublicVisibleById(characterId);
        if (c == null) {
            return null;
        }
        if (c.getStAvatarUrl() != null && !c.getStAvatarUrl().isBlank()) {
            try {
                StCharacterDetail d = stAdapter.getCharacter(new StCharacterGetRequest(c.getStAvatarUrl()));
                if (d != null && d.name() != null && !d.name().isBlank()) {
                    c.setName(d.name());
                    c.setDescription(d.description());
                    applyPublicProfile(c);
                    mapper.updateById(c);
                }
            } catch (StUnavailableException ex) {
                log.debug("ensureCharacter: 跳过 ST 刷新：{}", ex.getMessage());
            }
        }
        return c;
    }

    private AppCharacter refreshExistingSystemByAvatarUrl(String avatarUrl, String name, String description) {
        String safeAvatar = normalizeStAvatarUrl(avatarUrl);
        if (safeAvatar.isBlank()) {
            return null;
        }
        AppCharacter activePrivate = mapper.findActivePrivateByStAvatarUrl(safeAvatar);
        if (activePrivate != null) {
            log.warn("Skip ST catalog refresh for avatar shared with an active private character: {}", safeAvatar);
            return activePrivate;
        }
        AppCharacter existed = mapper.findActiveSystemByStAvatarUrl(safeAvatar);
        if (existed == null) {
            log.debug("Skip unknown ST character during catalog refresh: {}", safeAvatar);
            return null;
        }
        if (name != null && !name.isBlank()) {
            existed.setName(clip(name, MAX_NAME));
        }
        if (description != null) {
            existed.setDescription(description);
        }
        applyPublicProfile(existed);
        mapper.updateById(existed);
        return existed;
    }

    @Transactional
    public ImportedSystemCharacter createImportedSystemCharacter(String avatarUrl, StCharacterDetail detail) {
        String safeAvatar = normalizeStAvatarUrl(avatarUrl);
        if (safeAvatar.isBlank()) {
            return null;
        }
        if (mapper.findPrivateByStAvatarUrlAny(safeAvatar) != null
                || mapper.findSystemByStAvatarUrlAny(safeAvatar) != null) {
            throw new IllegalStateException("ST 角色文件名已被现有角色占用，系统导入已中止");
        }
        AppCharacter row = new AppCharacter();
        row.setStAvatarUrl(safeAvatar);
        row.setOwnerUserId(null);
        row.setPrivateCard(Boolean.FALSE);
        row.setReviewStatus(CharacterReviewStatus.APPROVED);
        row.setVipOnly(Boolean.FALSE);
        row.setUnlockedDefault(Boolean.TRUE);
        row.setClientVisible(Boolean.TRUE);
        row.setPreviewBlurVipLevel(0);
        row.setLikeCount(0);
        row.setDislikeCount(0);
        row.setSortOrder(0);
        boolean isNew = true;
        applyImportedDetail(row, safeAvatar, detail, isNew);
        mapper.insertFull(row);
        AppCharacter saved = mapper.findById(row.getId());
        int importedLorebookEntries = embeddedLorebookSyncService.replaceEmbeddedLorebook(
                row.getId(), detail == null ? null : detail.embeddedCharacterBookJson());
        return new ImportedSystemCharacter(saved, importedLorebookEntries);
    }

    private void applyImportedDetail(AppCharacter row, String avatarUrl, StCharacterDetail detail, boolean isNew) {
        row.setStAvatarUrl(clip(avatarUrl, MAX_ST_AVATAR_URL));
        if (isBlank(row.getReviewStatus())) {
            row.setReviewStatus(CharacterReviewStatus.APPROVED);
        }
        String fallbackName = detail == null ? "" : detail.name();
        if (isNew || isBlank(row.getName())) {
            row.setName(clip(isBlank(fallbackName) ? avatarUrl : fallbackName.trim(), MAX_NAME));
        } else if (!isBlank(fallbackName)) {
            row.setName(clip(fallbackName.trim(), MAX_NAME));
        }

        String description = detail == null ? "" : trimToEmpty(detail.description());
        if (isNew || isBlank(row.getDescription())) {
            row.setDescription(description);
        }
        if ((isNew || isBlank(row.getTagline())) && !description.isBlank()) {
            row.setTagline(buildImportedTagline(description));
        }
        if ((isNew || isBlank(row.getBio())) && !description.isBlank()) {
            row.setBio(description);
        }
        if ((isNew || isBlank(row.getPersona())) && detail != null && !isBlank(detail.personality())) {
            row.setPersona(detail.personality().trim());
        }
        if ((isNew || isBlank(row.getScenario())) && detail != null && !isBlank(detail.scenario())) {
            row.setScenario(detail.scenario().trim());
        }
        if ((isNew || isBlank(row.getFirstMessage())) && detail != null && !isBlank(detail.firstMes())) {
            row.setFirstMessage(detail.firstMes().trim());
        }
        if ((isNew || isBlank(row.getMesExample())) && detail != null && !isBlank(detail.mesExample())) {
            row.setMesExample(detail.mesExample().trim());
        }
        if ((isNew || isBlank(row.getSystemPrompt())) && detail != null && !isBlank(detail.systemPrompt())) {
            row.setSystemPrompt(detail.systemPrompt().trim());
        }
        if ((isNew || isBlank(row.getPostHistoryInstructions()))
                && detail != null
                && !isBlank(detail.postHistoryInstructions())) {
            row.setPostHistoryInstructions(detail.postHistoryInstructions().trim());
        }
        if ((isNew || isBlank(row.getCreatorNotes())) && detail != null && !isBlank(detail.creatorNotes())) {
            row.setCreatorNotes(detail.creatorNotes().trim());
        }
        if ((isNew || isBlank(row.getCreatorName())) && detail != null && !isBlank(detail.creator())) {
            row.setCreatorName(clip(detail.creator().trim(), MAX_CREATOR_NAME));
        }
        if ((isNew || isBlank(row.getTagsJson()))
                && detail != null
                && detail.tags() != null
                && !detail.tags().isEmpty()) {
            row.setTagsJson(toJsonArray(detail.tags()));
        }
        if ((isNew || isBlank(row.getAlternateGreetingsJson()))
                && detail != null
                && detail.alternateGreetings() != null
                && !detail.alternateGreetings().isEmpty()) {
            row.setAlternateGreetingsJson(toJsonArray(detail.alternateGreetings()));
        }
        boolean hasEmbeddedCharacterBook = detail != null && !trimToEmpty(detail.embeddedCharacterBookJson()).isBlank();
        if (!hasEmbeddedCharacterBook
                && (isNew || isBlank(row.getStWorldNamesJson()))
                && detail != null
                && detail.worldNames() != null
                && !detail.worldNames().isEmpty()) {
            row.setStWorldNamesJson(serializeImportedWorldNames(detail.worldNames()));
        }
        if ((isNew || isBlank(row.getStExtraJson())) && detail != null) {
            String rawJson = trimToEmpty(detail.rawJson());
            if (!rawJson.isBlank()) {
                row.setStExtraJson(prepareImportedExtraJson(rawJson, detail.embeddedCharacterBookJson()));
            } else if (!trimToEmpty(detail.embeddedCharacterBookJson()).isBlank()) {
                row.setStExtraJson(prepareImportedExtraJson(detail.embeddedCharacterBookJson(), null));
            }
        }
        if (isBlank(row.getAvatarUrl())) {
            row.setAvatarUrl(clip(avatarUrl, MAX_ST_AVATAR_URL));
        }
        applyPublicProfile(row);
    }

    public void applyPublicProfile(AppCharacter row) {
        publicProfileService.apply(row);
    }

    private static String buildImportedTagline(String value) {
        String text = trimToEmpty(value);
        if (text.isBlank()) {
            return "";
        }
        int lineBreak = text.indexOf('\n');
        if (lineBreak > 0) {
            text = text.substring(0, lineBreak).trim();
        }
        return clip(text, MAX_TAGLINE);
    }

    private String serializeImportedWorldNames(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return "[]";
        }
        try {
            return JSON.writeValueAsString(worldbookCatalogService.normalizeAndFilterAvailableWorldNames(requested));
        } catch (Exception ignored) {
            return toJsonArray(StWorldbookCatalogService.normalizeRequestedWorldNames(requested));
        }
    }

    private static String toJsonArray(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStAvatarUrl(String value) {
        String normalized = trimToEmpty(value);
        if (normalized.isBlank()) {
            return "";
        }
        if (!normalized.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            normalized = normalized + ".png";
        }
        return normalized;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private static String prepareImportedExtraJson(String primary, String secondary) {
        String first = trimToEmpty(primary);
        if (!first.isBlank() && first.length() <= SAFE_TEXT_COLUMN_LIMIT) {
            return first;
        }
        String fallback = trimToEmpty(secondary);
        if (!fallback.isBlank() && fallback.length() <= SAFE_TEXT_COLUMN_LIMIT) {
            return fallback;
        }
        String chosen = !first.isBlank() ? first : fallback;
        if (chosen.isBlank()) {
            return "";
        }
        return clip(chosen, SAFE_TEXT_COLUMN_LIMIT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ImportedSystemCharacter(AppCharacter character, int importedLorebookEntries) {
    }

}
