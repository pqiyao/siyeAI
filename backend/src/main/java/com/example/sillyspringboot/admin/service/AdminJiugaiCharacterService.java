package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.dto.OwnerPrivateCardCount;
import com.example.sillyspringboot.admin.web.dto.AdminCharacterPayload;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.entity.AppH5ClientUid;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.service.AppUserMessageService;
import com.example.sillyspringboot.compat.h5.web.CharacterJsonSupport;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.example.sillyspringboot.ops.service.TagLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AdminJiugaiCharacterService {

    private static final Logger log = LoggerFactory.getLogger(AdminJiugaiCharacterService.class);
    private static final DateTimeFormatter CREATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.CHINA);

    private final AppCharacterMapper characterMapper;
    private final AppLorebookEntryMapper lorebookEntryMapper;
    private final AppH5ClientUidMapper h5ClientUidMapper;
    private final AdminUserDisplayService userDisplayService;
    private final TagLibraryService tagLibraryService;
    private final AppUserMessageService userMessageService;
    private final CharacterReviewAuditLogService reviewAuditLogService;
    private final StWorldbookCatalogService worldbookCatalogService;
    private final StAdapter stAdapter;
    private final EmbeddedLorebookSyncService embeddedLorebookSyncService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public AdminJiugaiCharacterService(
            AppCharacterMapper characterMapper,
            AppLorebookEntryMapper lorebookEntryMapper,
            AppH5ClientUidMapper h5ClientUidMapper,
            AdminUserDisplayService userDisplayService,
            TagLibraryService tagLibraryService,
            AppUserMessageService userMessageService,
            CharacterReviewAuditLogService reviewAuditLogService,
            StWorldbookCatalogService worldbookCatalogService,
            StAdapter stAdapter,
            EmbeddedLorebookSyncService embeddedLorebookSyncService
    ) {
        this.characterMapper = characterMapper;
        this.lorebookEntryMapper = lorebookEntryMapper;
        this.h5ClientUidMapper = h5ClientUidMapper;
        this.userDisplayService = userDisplayService;
        this.tagLibraryService = tagLibraryService;
        this.userMessageService = userMessageService;
        this.reviewAuditLogService = reviewAuditLogService;
        this.worldbookCatalogService = worldbookCatalogService;
        this.stAdapter = stAdapter;
        this.embeddedLorebookSyncService = embeddedLorebookSyncService;
    }

    public Long resolveOwnerUserId(String ownerClientUid) {
        if (ownerClientUid == null || ownerClientUid.isBlank()) {
            return null;
        }
        String raw = ownerClientUid.trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("h5u_")) {
            try {
                return Long.parseLong(raw.substring(4).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        AppH5ClientUid bind = h5ClientUidMapper.findByClientUid(raw);
        return bind == null ? null : bind.getUserId();
    }

    public AdminCharacterPageResult listPage(
            int pageNum,
            int pageSize,
            String name,
            String scope,
            String ownerClientUid,
            String reviewStatus
    ) {
        int safePage = Math.max(0, pageNum - 1);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        String qName = name == null ? "" : name.trim();
        String safeScope = (scope == null || scope.isBlank()) ? "system" : scope.trim().toLowerCase(Locale.ROOT);
        Long ownerId = resolveOwnerUserId(ownerClientUid);
        String safeReviewStatus = normalizeReviewStatusFilter(reviewStatus);

        Boolean systemOnly = null;
        Boolean userOnly = null;
        if (ownerId == null) {
            if ("system".equals(safeScope)) {
                systemOnly = true;
            } else if ("user".equals(safeScope)) {
                userOnly = true;
            }
        }

        long total =
                characterMapper.countAdminList(
                        qName.isEmpty() ? null : qName,
                        ownerId,
                        systemOnly,
                        userOnly,
                        safeReviewStatus
                );
        List<AppCharacter> page =
                characterMapper.listAdminPage(
                        qName.isEmpty() ? null : qName,
                        ownerId,
                        systemOnly,
                        userOnly,
                        safeReviewStatus,
                        safePage * safeSize,
                        safeSize
                );

        Map<Long, LorebookSummary> lorebookSummaries = lorebookSummaryMap(page);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AppCharacter character : page) {
            AdminUserDisplayService.UserDisplayInfo ownerDisplay = AdminUserDisplayService.UserDisplayInfo.empty();
            if (character.getOwnerUserId() != null) {
                ownerDisplay = userDisplayService.resolve(character.getOwnerUserId());
            }
            rows.add(toTableRow(character, ownerDisplay, lorebookSummaries.get(character.getId())));
        }
        return new AdminCharacterPageResult(total, rows);
    }

    private Map<String, Object> toTableRow(
            AppCharacter character,
            AdminUserDisplayService.UserDisplayInfo ownerDisplay,
            LorebookSummary lorebookSummary
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", character.getId());
        row.put("name", character.getName());
        row.put("tagline", blank(character.getTagline()));
        String avatarUrl = blank(character.getAvatarUrl());
        if (avatarUrl.isEmpty()) {
            avatarUrl = blank(character.getCoverUrl());
        }
        if (avatarUrl.isEmpty()) {
            avatarUrl = blank(character.getStAvatarUrl());
        }
        row.put("avatarUrl", avatarUrl);
        row.put(
                "avatarHasInlineImage",
                avatarUrl.startsWith("data:image/") || (avatarUrl.length() > 200 && avatarUrl.startsWith("/9j/"))
        );
        row.put("userCreated", Boolean.TRUE.equals(character.getPrivateCard()));
        row.put(
                "ownerClientUid",
                ownerDisplay == null ? "" : blank(ownerDisplay.subLabel().isBlank() ? ownerDisplay.displayName() : ownerDisplay.subLabel())
        );
        row.put("ownerDisplayName", ownerDisplay == null ? "" : blank(ownerDisplay.displayName()));
        row.put("ownerSubLabel", ownerDisplay == null ? "" : blank(ownerDisplay.subLabel()));
        row.put("vipOnly", Boolean.TRUE.equals(character.getVipOnly()));
        row.put("clientVisible", !Boolean.FALSE.equals(character.getClientVisible()));
        row.put("previewBlurVipLevel", normalizePreviewBlurVipLevel(character.getPreviewBlurVipLevel()));
        row.put("reviewStatus", blank(character.getReviewStatus()));
        row.put("reviewReason", blank(character.getReviewReason()));
        row.put("reviewedBy", blank(character.getReviewedBy()));
        row.put("reviewedAt", character.getReviewedAt() == null ? "" : CREATE_FMT.format(character.getReviewedAt()));
        row.put("sortOrder", character.getSortOrder() != null ? character.getSortOrder() : 0);
        row.put("createTime", character.getCreatedAt() == null ? "" : CREATE_FMT.format(character.getCreatedAt()));
        row.put("lorebookSummary", lorebookSummaryMap(lorebookSummary));
        return row;
    }

    public Map<String, Object> toFormMap(AppCharacter character) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", character.getId());
        row.put("name", blank(character.getName()));
        row.put("tagline", blank(character.getTagline()));
        row.put("bio", blank(character.getBio()));
        row.put("persona", blank(character.getPersona()));
        row.put("scenario", blank(character.getScenario()));
        row.put("firstMessage", blank(character.getFirstMessage()));
        row.put("alternateGreetingsJson", normalizeJsonArray(character.getAlternateGreetingsJson()));
        row.put("mesExample", blank(character.getMesExample()));
        row.put("systemPrompt", blank(character.getSystemPrompt()));
        row.put("postHistoryInstructions", blank(character.getPostHistoryInstructions()));
        row.put("creatorNotes", blank(character.getCreatorNotes()));
        row.put("stExtraJson", blank(character.getStExtraJson()));
        row.put("avatarUrl", blank(character.getAvatarUrl()));
        row.put("coverUrl", blank(character.getCoverUrl()));
        row.put("chatBackgroundUrl", blank(character.getChatBackgroundUrl()));
        row.put("stWorldNames", parseWorldNames(character.getStWorldNamesJson()));
        row.put("stAvatarUrl", blank(character.getStAvatarUrl()));
        row.put("occupationLabel", blank(character.getOccupationLabel()));
        row.put("tagsJson", normalizeJsonArray(character.getTagsJson()));
        row.put("vipOnly", Boolean.TRUE.equals(character.getVipOnly()));
        row.put("unlockedDefault", !Boolean.FALSE.equals(character.getUnlockedDefault()));
        row.put("clientVisible", !Boolean.FALSE.equals(character.getClientVisible()));
        row.put("previewBlurVipLevel", normalizePreviewBlurVipLevel(character.getPreviewBlurVipLevel()));
        row.put("likeCount", character.getLikeCount() != null ? character.getLikeCount() : 0);
        row.put("dislikeCount", character.getDislikeCount() != null ? character.getDislikeCount() : 0);
        row.put("creatorName", blank(character.getCreatorName()));
        row.put("creatorHandle", blank(character.getCreatorHandle()));
        row.put("tokenDisplay", blank(character.getTokenDisplay()));
        row.put("gameplayType", blank(character.getGameplayType()));
        row.put("chatModesJson", blank(character.getChatModesJson()));
        row.put("sortOrder", character.getSortOrder() != null ? character.getSortOrder() : 0);
        row.put("privateCard", Boolean.TRUE.equals(character.getPrivateCard()));
        row.put("ownerUserId", character.getOwnerUserId());
        row.put("description", blank(character.getDescription()));
        row.put("reviewStatus", blank(character.getReviewStatus()));
        row.put("reviewReason", blank(character.getReviewReason()));
        row.put("reviewedBy", blank(character.getReviewedBy()));
        row.put("reviewedAt", character.getReviewedAt() == null ? "" : CREATE_FMT.format(character.getReviewedAt()));
        row.put("lorebookSummary", lorebookSummaryMap(lorebookSummary(character.getId())));
        return row;
    }

    private Map<Long, LorebookSummary> lorebookSummaryMap(List<AppCharacter> characters) {
        Map<Long, LorebookSummary> out = new LinkedHashMap<>();
        if (characters == null || characters.isEmpty()) {
            return out;
        }
        List<Long> ids = characters.stream()
                .filter(c -> c != null && c.getId() != null)
                .map(AppCharacter::getId)
                .toList();
        if (ids.isEmpty()) {
            return out;
        }
        for (Map<String, Object> row : lorebookEntryMapper.summarizeByCharacterIds(ids)) {
            LorebookSummary summary = toLorebookSummary(row);
            if (summary.characterId() > 0) {
                out.put(summary.characterId(), summary);
            }
        }
        return out;
    }

    private LorebookSummary lorebookSummary(Long characterId) {
        if (characterId == null || characterId <= 0) {
            return LorebookSummary.empty(0);
        }
        List<Map<String, Object>> rows = lorebookEntryMapper.summarizeByCharacterIds(List.of(characterId));
        if (rows == null || rows.isEmpty()) {
            return LorebookSummary.empty(characterId);
        }
        return toLorebookSummary(rows.get(0));
    }

    private static LorebookSummary toLorebookSummary(Map<String, Object> row) {
        if (row == null) {
            return LorebookSummary.empty(0);
        }
        long characterId = numberValue(firstPresent(row, "characterId", "characterid", "CHARACTERID", "character_id"));
        long total = numberValue(firstPresent(row, "totalCount", "totalcount", "TOTALCOUNT"));
        long embedded = numberValue(firstPresent(row, "embeddedCount", "embeddedcount", "EMBEDDEDCOUNT"));
        long manual = numberValue(firstPresent(row, "manualCount", "manualcount", "MANUALCOUNT"));
        long enabled = numberValue(firstPresent(row, "enabledCount", "enabledcount", "ENABLEDCOUNT"));
        return new LorebookSummary(characterId, total, embedded, manual, enabled);
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private static long numberValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static Map<String, Object> lorebookSummaryMap(LorebookSummary summary) {
        LorebookSummary safe = summary == null ? LorebookSummary.empty(0) : summary;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", safe.totalCount());
        out.put("embedded", safe.embeddedCount());
        out.put("manual", safe.manualCount());
        out.put("enabled", safe.enabledCount());
        out.put("hasEmbedded", safe.embeddedCount() > 0);
        out.put("hasManual", safe.manualCount() > 0);
        return out;
    }

    private record LorebookSummary(
            long characterId,
            long totalCount,
            long embeddedCount,
            long manualCount,
            long enabledCount
    ) {
        static LorebookSummary empty(long characterId) {
            return new LorebookSummary(characterId, 0, 0, 0, 0);
        }
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return "[]";
        }
        return value.trim();
    }

    public void applyPayload(AppCharacter row, AdminCharacterPayload payload) {
        row.setName(payload.getName() == null ? "" : payload.getName().trim());
        row.setTagline(payload.getTagline());
        row.setBio(payload.getBio());
        row.setPersona(payload.getPersona());
        row.setScenario(payload.getScenario());
        row.setFirstMessage(payload.getFirstMessage());
        row.setAlternateGreetingsJson(normalizeJsonArray(payload.getAlternateGreetingsJson()));
        row.setMesExample(payload.getMesExample());
        row.setSystemPrompt(payload.getSystemPrompt());
        row.setPostHistoryInstructions(payload.getPostHistoryInstructions());
        row.setCreatorNotes(payload.getCreatorNotes());
        row.setStExtraJson(payload.getStExtraJson());
        row.setAvatarUrl(payload.getAvatarUrl());
        row.setCoverUrl(payload.getCoverUrl());
        row.setChatBackgroundUrl(payload.getChatBackgroundUrl());
        if (payload.getStWorldNames() != null) {
            row.setStWorldNamesJson(serializeWorldNames(payload.getStWorldNames()));
        }
        if (payload.getStAvatarUrl() != null && !payload.getStAvatarUrl().isBlank()) {
            row.setStAvatarUrl(payload.getStAvatarUrl().trim());
        }
        row.setOccupationLabel(payload.getOccupationLabel());
        row.setTagsJson(normalizeJsonArray(payload.getTagsJson()));
        if (payload.getVipOnly() != null) {
            row.setVipOnly(payload.getVipOnly());
        }
        if (payload.getUnlockedDefault() != null) {
            row.setUnlockedDefault(payload.getUnlockedDefault());
        }
        if (payload.getClientVisible() != null) {
            row.setClientVisible(payload.getClientVisible());
        }
        if (payload.getPreviewBlurVipLevel() != null) {
            row.setPreviewBlurVipLevel(normalizePreviewBlurVipLevel(payload.getPreviewBlurVipLevel()));
        }
        if (payload.getLikeCount() != null) {
            row.setLikeCount(payload.getLikeCount());
        }
        if (payload.getDislikeCount() != null) {
            row.setDislikeCount(payload.getDislikeCount());
        }
        row.setCreatorName(payload.getCreatorName());
        row.setCreatorHandle(payload.getCreatorHandle());
        row.setTokenDisplay(payload.getTokenDisplay());
        row.setGameplayType(payload.getGameplayType());
        row.setChatModesJson(payload.getChatModesJson());
        if (payload.getSortOrder() != null) {
            row.setSortOrder(payload.getSortOrder());
        }
        String bio = payload.getBio() == null ? "" : payload.getBio().strip();
        String tagline = payload.getTagline() == null ? "" : payload.getTagline().strip();
        row.setDescription(bio.isBlank() ? tagline : bio);
    }

    @Transactional
    public AppCharacter createFromPayload(AdminCharacterPayload payload) {
        AppCharacter row = new AppCharacter();
        String stAvatarUrl =
                payload.getStAvatarUrl() != null && !payload.getStAvatarUrl().isBlank()
                        ? payload.getStAvatarUrl().trim()
                        : ("admin_" + UUID.randomUUID() + ".png");
        while (characterMapper.findByStAvatarUrl(stAvatarUrl) != null) {
            stAvatarUrl = "admin_" + UUID.randomUUID() + ".png";
        }
        row.setStAvatarUrl(stAvatarUrl);
        row.setOwnerUserId(null);
        row.setPrivateCard(Boolean.FALSE);
        row.setReviewStatus(CharacterReviewStatus.APPROVED);
        row.setVipOnly(payload.getVipOnly() != null ? payload.getVipOnly() : Boolean.FALSE);
        row.setUnlockedDefault(payload.getUnlockedDefault() != null ? payload.getUnlockedDefault() : Boolean.TRUE);
        row.setClientVisible(payload.getClientVisible() != null ? payload.getClientVisible() : Boolean.TRUE);
        row.setPreviewBlurVipLevel(normalizePreviewBlurVipLevel(payload.getPreviewBlurVipLevel()));
        row.setLikeCount(payload.getLikeCount() != null ? payload.getLikeCount() : 0);
        row.setDislikeCount(payload.getDislikeCount() != null ? payload.getDislikeCount() : 0);
        row.setSortOrder(payload.getSortOrder() != null ? payload.getSortOrder() : 0);
        applyPayload(row, payload);
        row.setStAvatarUrl(stAdapter.syncCharacterCard(toStDetail(row), row.getStAvatarUrl()));
        characterMapper.insertFull(row);
        tagLibraryService.ensureTagsExist(CharacterJsonSupport.parseStringArrayJson(row.getTagsJson()));
        return characterMapper.findById(row.getId());
    }

    @Transactional
    public AppCharacter updateFromPayload(AdminCharacterPayload payload) {
        if (payload.getId() == null) {
            return null;
        }
        AppCharacter row = characterMapper.findById(payload.getId());
        if (row == null || row.getDeletedAt() != null) {
            return null;
        }
        LocalDateTime createdAt = row.getCreatedAt();
        Long originalOwnerUserId = row.getOwnerUserId();
        Boolean originalPrivateCard = row.getPrivateCard();
        applyPayload(row, payload);
        row.setOwnerUserId(originalOwnerUserId);
        row.setPrivateCard(Boolean.TRUE.equals(originalPrivateCard) || originalOwnerUserId != null);
        row.setCreatedAt(createdAt);
        row.setStAvatarUrl(stAdapter.syncCharacterCard(toStDetail(row), row.getStAvatarUrl()));
        characterMapper.updateById(row);
        tagLibraryService.ensureTagsExist(CharacterJsonSupport.parseStringArrayJson(row.getTagsJson()));
        return characterMapper.findById(payload.getId());
    }

    private static StCharacterDetail toStDetail(AppCharacter row) {
        return new StCharacterDetail(
                blank(row == null ? null : row.getName()),
                blank(row == null ? null : row.getStAvatarUrl()),
                blank(row == null ? null : row.getDescription()),
                blank(row == null ? null : row.getScenario()),
                blank(row == null ? null : row.getFirstMessage()),
                blank(row == null ? null : row.getPersona()),
                CharacterJsonSupport.parseStringArrayJson(row == null ? null : row.getTagsJson()),
                CharacterJsonSupport.parseStringArrayJson(row == null ? null : row.getAlternateGreetingsJson()),
                blank(row == null ? null : row.getMesExample()),
                blank(row == null ? null : row.getSystemPrompt()),
                blank(row == null ? null : row.getPostHistoryInstructions()),
                blank(row == null ? null : row.getCreatorNotes()),
                blank(row == null ? null : row.getCreatorName()),
                CharacterJsonSupport.parseStringArrayJson(row == null ? null : row.getStWorldNamesJson()),
                blank(row == null ? null : row.getStExtraJson()),
                blank(row == null ? null : row.getStExtraJson()));
    }

    @Transactional
    public AppCharacter reviewCharacter(long id, String reviewStatus, String reviewReason, String reviewedBy) {
        List<AppCharacter> reviewed = reviewCharacters(List.of(id), reviewStatus, reviewReason, reviewedBy);
        return reviewed.isEmpty() ? null : reviewed.get(0);
    }

    @Transactional
    public List<AppCharacter> reviewCharacters(List<Long> ids, String reviewStatus, String reviewReason, String reviewedBy) {
        List<Long> targetIds = normalizeIdList(ids);
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("缺少角色 id");
        }
        String safeStatus = reviewStatus == null ? "" : reviewStatus.trim().toUpperCase(Locale.ROOT);
        if (!CharacterReviewStatus.isValid(safeStatus)) {
            throw new IllegalArgumentException("审核状态不合法");
        }
        String safeReason = reviewReason == null ? null : reviewReason.trim();
        if (safeReason != null && safeReason.length() > 500) {
            safeReason = safeReason.substring(0, 500);
        }
        if (!CharacterReviewStatus.REJECTED.equals(safeStatus)) {
            safeReason = null;
        }
        String safeReviewedBy = reviewedBy == null || reviewedBy.isBlank() ? "admin" : reviewedBy.trim();
        String batchNo = targetIds.size() > 1 ? nextReviewBatchNo() : null;

        List<AppCharacter> reviewed = new ArrayList<>();
        for (Long id : targetIds) {
            AppCharacter row = characterMapper.findById(id);
            if (row == null || row.getDeletedAt() != null) {
                throw new IllegalStateException("角色不存在");
            }
            if (!Boolean.TRUE.equals(row.getPrivateCard()) || row.getOwnerUserId() == null) {
                throw new IllegalStateException("仅用户创建的角色卡支持审核");
            }
            characterMapper.updateReviewById(id, safeStatus, safeReason, safeReviewedBy);
            AppCharacter saved = characterMapper.findById(id);
            reviewAuditLogService.recordReview(saved, safeStatus, safeReason, safeReviewedBy, batchNo);
            if (CharacterReviewStatus.REJECTED.equals(safeStatus)) {
                userMessageService.sendCharacterRejectedMessage(saved, safeReason);
            }
            reviewed.add(saved);
        }
        return reviewed;
    }

    @Transactional
    public void softDeleteIds(String ids) {
        List<Long> idList = parseIds(ids);
        for (Long id : idList) {
            embeddedLorebookSyncService.deleteAllForCharacter(id);
            characterMapper.softDeleteById(id);
        }
    }

    @Transactional
    public RemoveSummary removeIds(String ids, boolean syncStFile) {
        List<Long> idList = parseIds(ids);
        int localDeleted = 0;
        int stDeleted = 0;
        for (Long id : idList) {
            AppCharacter row = characterMapper.findById(id);
            if (row == null || row.getDeletedAt() != null) {
                continue;
            }
            if (syncStFile) {
                String avatarUrl = row.getStAvatarUrl() == null ? "" : row.getStAvatarUrl().trim();
                if (!avatarUrl.isBlank() && stAdapter.deleteCharacter(avatarUrl, false)) {
                    stDeleted++;
                }
            }
            embeddedLorebookSyncService.deleteAllForCharacter(id);
            characterMapper.softDeleteById(id);
            localDeleted++;
        }
        return new RemoveSummary(localDeleted, stDeleted);
    }

    private static List<Long> parseIds(String ids) {
        List<Long> out = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return out;
        }
        for (String part : ids.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // ignore bad id
            }
        }
        return out;
    }

    public record RemoveSummary(int localDeleted, int stDeleted) {
    }

    private static List<Long> normalizeIdList(List<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids == null) {
            return List.of();
        }
        for (Long id : ids) {
            if (id != null && id > 0) {
                out.add(id);
            }
        }
        return new ArrayList<>(out);
    }

    private static String nextReviewBatchNo() {
        return "RV"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private List<String> parseWorldNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = objectMapper.readValue(raw, List.class);
            if (list == null || list.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String text = item == null ? "" : String.valueOf(item).trim();
                if (!text.isBlank()) {
                    out.add(text);
                }
            }
            return List.copyOf(out);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String serializeWorldNames(List<String> requested) {
        StWorldbookCatalogService.WorldbookResolution resolution = worldbookCatalogService.resolveWorldNames(requested);
        if (!resolution.missing().isEmpty()) {
            log.warn("admin character save ignored missing ST worldbooks: {}", String.join(", ", resolution.missing()));
        }
        try {
            return objectMapper.writeValueAsString(resolution.matched());
        } catch (Exception e) {
            throw new IllegalArgumentException("worldbooks 保存失败");
        }
    }

    public Map<String, Object> userCreatedStats(int limit) {
        List<OwnerPrivateCardCount> grouped = characterMapper.countPrivateByOwnerGrouped();
        long total = 0;
        for (OwnerPrivateCardCount item : grouped) {
            total += item.getCnt() == null ? 0 : item.getCnt();
        }
        List<Map<String, Object>> topOwners = new ArrayList<>();
        grouped.stream()
                .sorted((a, b) -> Long.compare(b.getCnt() == null ? 0 : b.getCnt(), a.getCnt() == null ? 0 : a.getCnt()))
                .limit(Math.min(200, Math.max(1, limit)))
                .forEach(item -> {
                    long userId = item.getOwnerUserId() == null ? 0 : item.getOwnerUserId();
                    AdminUserDisplayService.UserDisplayInfo ownerDisplay =
                            userId > 0 ? userDisplayService.resolve(userId) : AdminUserDisplayService.UserDisplayInfo.empty();
                    String label = blank(ownerDisplay.displayName());
                    if (label.isBlank()) {
                        label = userId > 0 ? ("user#" + userId) : "(unknown)";
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(
                            "ownerClientUid",
                            blank(ownerDisplay.subLabel().isBlank() ? ownerDisplay.displayName() : ownerDisplay.subLabel())
                    );
                    row.put("ownerDisplayName", label);
                    row.put("ownerSubLabel", blank(ownerDisplay.subLabel()));
                    row.put("count", item.getCnt());
                    topOwners.add(row);
                });
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUserCreated", total);
        data.put("ownerCount", grouped.size());
        data.put("topOwners", topOwners);
        return data;
    }

    private static String normalizeReviewStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return CharacterReviewStatus.isValid(normalized) ? normalized : null;
    }

    private static int normalizePreviewBlurVipLevel(Integer value) {
        int level = value == null ? 0 : value;
        if (level < 0) {
            return 0;
        }
        if (level > 2) {
            return 2;
        }
        return level;
    }
}
