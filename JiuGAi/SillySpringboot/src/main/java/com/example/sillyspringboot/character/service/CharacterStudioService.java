package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.entity.AppCharacterOpening;
import com.example.sillyspringboot.character.entity.AppCharacterOpeningSegment;
import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.compat.h5.entity.H5MyCharacter;
import com.example.sillyspringboot.compat.h5.mapper.H5MyCharacterMapper;
import com.example.sillyspringboot.compat.h5.web.dto.H5MyCharacterSaveRequest;
import com.example.sillyspringboot.ops.entity.AppUserTtsVoiceBinding;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceBindingMapper;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
public class CharacterStudioService {
    private static final int MAX_MEMBERS = 8;
    private static final int MAX_OPENINGS = 20;
    private static final int MAX_OPENING_SEGMENTS = 20;
    private static final int MAX_LOREBOOK_ENTRIES = 300;

    private final CharacterStudioMapper studioMapper;
    private final AppLorebookEntryMapper lorebookMapper;
    private final AppUserTtsVoiceBindingMapper voiceBindingMapper;
    private final UserTtsVoiceService userTtsVoiceService;
    private final AppFeatureSettingsService featureSettingsService;
    private final H5MyCharacterMapper myCharacterMapper;

    public CharacterStudioService(
            CharacterStudioMapper studioMapper,
            AppLorebookEntryMapper lorebookMapper,
            AppUserTtsVoiceBindingMapper voiceBindingMapper,
            UserTtsVoiceService userTtsVoiceService,
            AppFeatureSettingsService featureSettingsService,
            H5MyCharacterMapper myCharacterMapper
    ) {
        this.studioMapper = studioMapper;
        this.lorebookMapper = lorebookMapper;
        this.voiceBindingMapper = voiceBindingMapper;
        this.userTtsVoiceService = userTtsVoiceService;
        this.featureSettingsService = featureSettingsService;
        this.myCharacterMapper = myCharacterMapper;
    }

    @Transactional
    public void replaceStudio(long userId, long characterId, H5MyCharacterSaveRequest request) {
        requireOwnedPrivateCharacter(userId, characterId);
        String previousCardType = normalizeCardType(studioMapper.findCardType(characterId));
        String cardType = request.getCardType() == null
                ? previousCardType
                : normalizeCardType(request.getCardType());
        if (request.getCardType() != null) {
            studioMapper.updateCardType(characterId, cardType);
        }

        Map<String, Long> memberIds = new LinkedHashMap<>();
        if (request.getMembers() != null) {
            AppFeatureSettings featureSettings = featureSettingsService.getSettings();
            boolean voiceFeatureEnabled = featureSettings == null || featureSettings.isVoiceFeatureEnabled();
            validateMemberReplacementShape(characterId, request);
            if (request.getOpenings() != null) {
                studioMapper.deleteOpeningSegmentsByCharacterId(characterId);
                studioMapper.deleteOpeningsByCharacterId(characterId);
            }
            if (request.getLorebookEntries() != null) {
                studioMapper.clearLorebookMemberScopes(characterId);
            }
            memberIds = replaceMembers(
                    userId, characterId, cardType, request.getMembers(), voiceFeatureEnabled);
        } else {
            indexExistingMembers(studioMapper.listMembers(characterId), memberIds);
        }

        migrateVoiceBindingScope(userId, characterId, previousCardType, cardType);
        applyRequestedVoiceBindings(userId, characterId, cardType, request.getMembers(), memberIds);

        if (request.getOpenings() != null) {
            replaceOpenings(characterId, request.getOpenings(), memberIds);
        }
        if (request.getLorebookEntries() != null) {
            replaceLorebook(characterId, request.getLorebookEntries(), memberIds);
        }
    }

    @Transactional
    public void deleteVoiceBindings(long userId, long characterId) {
        requireOwnedPrivateCharacter(userId, characterId);
        voiceBindingMapper.deleteCharacterScopes(userId, characterId);
    }

    private void requireOwnedPrivateCharacter(long userId, long characterId) {
        if (myCharacterMapper.findEditor(characterId, userId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
    }

    public Map<String, Object> loadStudio(H5MyCharacter character) {
        long characterId = character.getId();
        List<AppCharacterMember> members = studioMapper.listMembers(characterId);
        List<AppCharacterOpening> openings = studioMapper.listOpenings(characterId);
        List<AppCharacterOpeningSegment> segments = studioMapper.listSegmentsByCharacter(characterId);
        List<AppLorebookEntry> lorebook = lorebookMapper.listPageByCharacterId(characterId, 0, MAX_LOREBOOK_ENTRIES);

        Map<Long, List<AppCharacterOpeningSegment>> segmentsByOpening = new HashMap<>();
        for (AppCharacterOpeningSegment segment : segments) {
            segmentsByOpening.computeIfAbsent(segment.getOpeningId(), ignored -> new ArrayList<>()).add(segment);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cardType", normalizeCardType(character.getCardType()));
        out.put("members", members.isEmpty() ? legacyMembers(character) : members.stream().map(this::memberMap).toList());
        out.put("openings", openings.isEmpty()
                ? legacyOpenings(character)
                : openings.stream().map(opening -> openingMap(opening, segmentsByOpening.get(opening.getId()))).toList());
        out.put("lorebookEntries", lorebook.stream().map(this::lorebookMap).toList());
        return out;
    }

    private Map<String, Long> replaceMembers(
            long userId,
            long characterId,
            String cardType,
            List<H5MyCharacterSaveRequest.MemberInput> inputs,
            boolean voiceFeatureEnabled
    ) {
        List<H5MyCharacterSaveRequest.MemberInput> usable = inputs.stream()
                .filter(input -> input != null && hasText(input.getName()))
                .toList();
        int minimum = "ENSEMBLE".equals(cardType) ? 2 : 1;
        if (usable.size() < minimum || usable.size() > MAX_MEMBERS) {
            throw validation("Member count must be between " + minimum + " and " + MAX_MEMBERS + ".");
        }

        List<AppCharacterMember> existingMembers = studioMapper.listMembers(characterId);
        Map<Long, AppCharacterMember> existingById = new HashMap<>();
        for (AppCharacterMember existing : existingMembers) {
            if (existing != null && existing.getId() != null) existingById.put(existing.getId(), existing);
        }
        Map<String, Long> ids = new LinkedHashMap<>();
        Set<String> clientKeys = new HashSet<>();
        Set<Long> retainedIds = new HashSet<>();
        boolean primaryAssigned = false;
        for (int i = 0; i < usable.size(); i++) {
            H5MyCharacterSaveRequest.MemberInput input = usable.get(i);
            String clientKey = memberKey(input.getClientKey(), i);
            if (!clientKeys.add(clientKey)) {
                throw validation("Character member clientKey must be unique.");
            }
            AppCharacterMember member = new AppCharacterMember();
            member.setCharacterId(characterId);
            member.setName(clip(input.getName(), 64));
            member.setTagline(clip(input.getTagline(), 255));
            member.setPersona(clip(input.getPersona(), 12000));
            member.setAvatarUrl(clip(input.getAvatarUrl(), 512));
            member.setImageReferenceUrl(clip(input.getImageReferenceUrl(), 512));
            boolean primary = !primaryAssigned && (Boolean.TRUE.equals(input.getPrimaryMember()) || i == 0);
            member.setPrimaryMember(primary);
            member.setSortOrder(i);
            member.setEnabled(true);
            Long requestedId = input.getId();
            if (requestedId != null && requestedId > 0 && existingById.containsKey(requestedId)) {
                if (retainedIds.contains(requestedId)) {
                    throw validation("Character member id must be unique.");
                }
                member.setId(requestedId);
                member.setVoiceConfigJson(voiceFeatureEnabled
                        ? clip(input.getVoiceConfigJson(), 12000)
                        : nullToEmpty(existingById.get(requestedId).getVoiceConfigJson()));
                if (studioMapper.updateMember(member) != 1) {
                    throw new IllegalStateException("failed to update character member " + requestedId);
                }
                retainedIds.add(requestedId);
            } else {
                member.setVoiceConfigJson(voiceFeatureEnabled ? clip(input.getVoiceConfigJson(), 12000) : "");
                studioMapper.insertMember(member);
                retainedIds.add(member.getId());
            }
            primaryAssigned = primaryAssigned || primary;
            ids.put(clientKey, member.getId());
        }
        for (AppCharacterMember existing : existingMembers) {
            if (existing != null && existing.getId() != null && !retainedIds.contains(existing.getId())) {
                voiceBindingMapper.deleteMemberScope(userId, characterId, existing.getId());
                studioMapper.deleteMemberById(characterId, existing.getId());
            }
        }
        return ids;
    }

    private void validateMemberReplacementShape(long characterId, H5MyCharacterSaveRequest request) {
        List<AppCharacterMember> existing = studioMapper.listMembers(characterId);
        if (existing == null || existing.isEmpty()) return;
        Set<Long> requestedIds = request.getMembers().stream()
                .filter(input -> input != null && input.getId() != null && input.getId() > 0)
                .map(H5MyCharacterSaveRequest.MemberInput::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean removesExistingMember = existing.stream()
                .anyMatch(member -> member != null && member.getId() != null && !requestedIds.contains(member.getId()));
        if (removesExistingMember && (request.getOpenings() == null || request.getLorebookEntries() == null)) {
            throw validation("Deleting character members requires complete openings and worldbook data.");
        }
    }

    private void applyRequestedVoiceBindings(
            long userId,
            long characterId,
            String cardType,
            List<H5MyCharacterSaveRequest.MemberInput> inputs,
            Map<String, Long> memberIds
    ) {
        if (inputs == null || inputs.stream().noneMatch(input -> input != null
                && Boolean.TRUE.equals(input.getVoiceBindingChanged()))) return;
        featureSettingsService.ensureVoiceFeatureEnabled();
        for (int i = 0; i < inputs.size(); i++) {
            H5MyCharacterSaveRequest.MemberInput input = inputs.get(i);
            if (input == null || !Boolean.TRUE.equals(input.getVoiceBindingChanged())) continue;
            Long voiceId = input.getUserTtsVoiceId();
            if ("ENSEMBLE".equals(cardType)) {
                Long memberId = memberIds.get(memberKey(input.getClientKey(), i));
                if (memberId == null || memberId <= 0) {
                    throw validation("Voice binding references a missing character member.");
                }
                userTtsVoiceService.saveBinding(userId, "MEMBER", characterId, memberId, voiceId);
            } else if (i == 0) {
                userTtsVoiceService.saveBinding(userId, "CHARACTER", characterId, 0, voiceId);
            }
        }
    }

    private void migrateVoiceBindingScope(
            long userId,
            long characterId,
            String previousCardType,
            String cardType
    ) {
        if (previousCardType.equals(cardType)) return;
        List<AppCharacterMember> currentMembers = studioMapper.listMembers(characterId);
        long primaryMemberId = currentMembers.isEmpty() || currentMembers.get(0).getId() == null
                ? 0L
                : currentMembers.get(0).getId();

        if ("ENSEMBLE".equals(cardType)) {
            AppUserTtsVoiceBinding oldBinding = voiceBindingMapper.find(userId, "CHARACTER", characterId, 0);
            if (oldBinding != null && primaryMemberId > 0) {
                saveVoiceBinding(userId, "MEMBER", characterId, primaryMemberId, oldBinding.getVoiceId());
            }
            voiceBindingMapper.deleteScope(userId, "CHARACTER", characterId, 0);
            return;
        }

        AppUserTtsVoiceBinding oldBinding = primaryMemberId > 0
                ? voiceBindingMapper.find(userId, "MEMBER", characterId, primaryMemberId)
                : null;
        if (oldBinding != null) {
            saveVoiceBinding(userId, "CHARACTER", characterId, 0, oldBinding.getVoiceId());
        }
        voiceBindingMapper.deleteMemberScopes(userId, characterId);
    }

    private void saveVoiceBinding(
            long userId,
            String scopeType,
            long characterId,
            long memberId,
            Long voiceId
    ) {
        if (voiceId == null || voiceId <= 0) return;
        AppUserTtsVoiceBinding row = new AppUserTtsVoiceBinding();
        row.setUserId(userId);
        row.setScopeType(scopeType);
        row.setCharacterId(characterId);
        row.setMemberId(memberId);
        row.setVoiceId(voiceId);
        if (voiceBindingMapper.updateVoice(row) != 0) return;
        try {
            voiceBindingMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            voiceBindingMapper.updateVoice(row);
        }
    }

    private void replaceOpenings(
            long characterId,
            List<H5MyCharacterSaveRequest.OpeningInput> inputs,
            Map<String, Long> memberIds
    ) {
        if (inputs.size() > MAX_OPENINGS) {
            throw validation("Up to " + MAX_OPENINGS + " opening scenes are allowed.");
        }
        boolean defaultAssigned = false;
        for (int i = 0; i < inputs.size(); i++) {
            H5MyCharacterSaveRequest.OpeningInput input = inputs.get(i);
            if (input == null || input.getSegments() == null || input.getSegments().isEmpty()) {
                continue;
            }
            List<H5MyCharacterSaveRequest.OpeningSegmentInput> segments = input.getSegments().stream()
                    .filter(segment -> segment != null && hasText(segment.getContent()))
                    .toList();
            if (segments.isEmpty()) {
                continue;
            }
            if (segments.size() > MAX_OPENING_SEGMENTS) {
                throw validation("Each opening can contain up to " + MAX_OPENING_SEGMENTS + " segments.");
            }

            AppCharacterOpening opening = new AppCharacterOpening();
            opening.setCharacterId(characterId);
            opening.setTitle(clip(firstNonBlank(input.getTitle(), "Opening " + (i + 1)), 80));
            opening.setSummary(clip(input.getSummary(), 255));
            opening.setScenarioOverride(clip(input.getScenarioOverride(), 12000));
            boolean makeDefault = !defaultAssigned && (Boolean.TRUE.equals(input.getDefaultOpening()) || i == 0);
            opening.setDefaultOpening(makeDefault);
            opening.setSortOrder(i);
            opening.setEnabled(true);
            studioMapper.insertOpening(opening);
            defaultAssigned = defaultAssigned || makeDefault;

            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                H5MyCharacterSaveRequest.OpeningSegmentInput inputSegment = segments.get(segmentIndex);
                String speakerType = normalizeSpeakerType(inputSegment.getSpeakerType());
                AppCharacterOpeningSegment segment = new AppCharacterOpeningSegment();
                segment.setOpeningId(opening.getId());
                segment.setSpeakerType(speakerType);
                segment.setSpeakerMemberId("CHARACTER".equals(speakerType)
                        ? resolveMemberId(inputSegment.getSpeakerClientKey(), memberIds)
                        : null);
                if ("CHARACTER".equals(speakerType) && segment.getSpeakerMemberId() == null) {
                    throw validation("Opening segment references a missing character member.");
                }
                segment.setContent(clip(inputSegment.getContent(), 12000));
                segment.setSortOrder(segmentIndex);
                studioMapper.insertOpeningSegment(segment);
            }
        }
    }

    private void replaceLorebook(
            long characterId,
            List<H5MyCharacterSaveRequest.LorebookInput> inputs,
            Map<String, Long> memberIds
    ) {
        if (inputs.size() > MAX_LOREBOOK_ENTRIES) {
            throw validation("Up to " + MAX_LOREBOOK_ENTRIES + " worldbook entries are allowed.");
        }
        lorebookMapper.deleteByCharacterId(characterId);
        for (H5MyCharacterSaveRequest.LorebookInput input : inputs) {
            if (input == null || !hasText(input.getContent())) {
                continue;
            }
            AppLorebookEntry entry = new AppLorebookEntry();
            entry.setCharacterId(characterId);
            entry.setTitle(clip(input.getTitle(), 120));
            entry.setMemberId(resolveOptionalMemberId(input.getMemberClientKey(), memberIds));
            entry.setKeywordsCsv(joinKeywords(input.getKeywords()));
            entry.setSecondaryKeywordsCsv(joinKeywords(input.getSecondaryKeywords()));
            entry.setMatchMode(normalizeEnum(input.getMatchMode(), List.of("ANY", "ALL"), "ANY"));
            entry.setContent(clip(input.getContent(), 30000));
            entry.setPriority(clamp(input.getPriority(), 0, 1000, 100));
            entry.setConstantInjection(Boolean.TRUE.equals(input.getConstantInjection()));
            entry.setScanDepth(clamp(input.getScanDepth(), 1, 100, 8));
            entry.setInjectionPosition(normalizeEnum(
                    input.getInjectionPosition(),
                    List.of("BEFORE_CHARACTER", "AFTER_CHARACTER", "BEFORE_HISTORY"),
                    "BEFORE_CHARACTER"
            ));
            entry.setEnabled(!Boolean.FALSE.equals(input.getEnabled()));
            entry.setSource("manual");
            lorebookMapper.insert(entry);
        }
    }

    private List<Map<String, Object>> legacyMembers(H5MyCharacter character) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("clientKey", "legacy_primary");
        member.put("id", "");
        member.put("name", nullToEmpty(character.getName()));
        member.put("tagline", nullToEmpty(character.getTagline()));
        member.put("persona", nullToEmpty(character.getPersona()));
        member.put("avatarUrl", nullToEmpty(character.getAvatarUrl()));
        member.put("imageReferenceUrl", nullToEmpty(character.getAvatarUrl()));
        member.put("voiceConfigJson", "");
        member.put("primaryMember", true);
        return List.of(member);
    }

    private List<Map<String, Object>> legacyOpenings(H5MyCharacter character) {
        List<String> greetings = new ArrayList<>();
        if (hasText(character.getFirstMessage())) greetings.add(character.getFirstMessage());
        if (character.getAlternateGreetings() != null) {
            character.getAlternateGreetings().stream().filter(CharacterStudioService::hasText).forEach(greetings::add);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < greetings.size(); i++) {
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("speakerClientKey", "legacy_primary");
            segment.put("speakerType", "CHARACTER");
            segment.put("content", greetings.get(i));
            Map<String, Object> opening = new LinkedHashMap<>();
            opening.put("id", "");
            opening.put("title", "Opening " + (i + 1));
            opening.put("summary", preview(greetings.get(i)));
            opening.put("scenarioOverride", "");
            opening.put("defaultOpening", i == 0);
            opening.put("segments", List.of(segment));
            out.add(opening);
        }
        return out;
    }

    private Map<String, Object> memberMap(AppCharacterMember member) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", member.getId());
        out.put("clientKey", "m_" + member.getId());
        out.put("name", member.getName());
        out.put("tagline", nullToEmpty(member.getTagline()));
        out.put("persona", nullToEmpty(member.getPersona()));
        out.put("avatarUrl", nullToEmpty(member.getAvatarUrl()));
        out.put("voiceConfigJson", nullToEmpty(member.getVoiceConfigJson()));
        out.put("imageReferenceUrl", nullToEmpty(member.getImageReferenceUrl()));
        out.put("primaryMember", Boolean.TRUE.equals(member.getPrimaryMember()));
        return out;
    }

    private Map<String, Object> openingMap(AppCharacterOpening opening, List<AppCharacterOpeningSegment> segments) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", opening.getId());
        out.put("title", opening.getTitle());
        out.put("summary", nullToEmpty(opening.getSummary()));
        out.put("scenarioOverride", nullToEmpty(opening.getScenarioOverride()));
        out.put("defaultOpening", Boolean.TRUE.equals(opening.getDefaultOpening()));
        out.put("segments", segments == null ? List.of() : segments.stream().map(segment -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("speakerClientKey", segment.getSpeakerMemberId() == null ? "" : "m_" + segment.getSpeakerMemberId());
            row.put("speakerType", segment.getSpeakerType());
            row.put("content", segment.getContent());
            return row;
        }).toList());
        return out;
    }

    private Map<String, Object> lorebookMap(AppLorebookEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", entry.getId());
        out.put("title", nullToEmpty(entry.getTitle()));
        out.put("memberClientKey", entry.getMemberId() == null ? "" : "m_" + entry.getMemberId());
        out.put("keywords", splitKeywords(entry.getKeywordsCsv()));
        out.put("secondaryKeywords", splitKeywords(entry.getSecondaryKeywordsCsv()));
        out.put("matchMode", firstNonBlank(entry.getMatchMode(), "ANY"));
        out.put("content", nullToEmpty(entry.getContent()));
        out.put("priority", entry.getPriority() == null ? 100 : entry.getPriority());
        out.put("constantInjection", Boolean.TRUE.equals(entry.getConstantInjection()));
        out.put("scanDepth", entry.getScanDepth() == null ? 8 : entry.getScanDepth());
        out.put("injectionPosition", firstNonBlank(entry.getInjectionPosition(), "BEFORE_CHARACTER"));
        out.put("enabled", !Boolean.FALSE.equals(entry.getEnabled()));
        return out;
    }

    private static void indexExistingMembers(List<AppCharacterMember> members, Map<String, Long> target) {
        for (int i = 0; i < members.size(); i++) {
            AppCharacterMember member = members.get(i);
            target.put("m_" + member.getId(), member.getId());
            target.put("member_" + i, member.getId());
        }
    }

    private static Long resolveMemberId(String clientKey, Map<String, Long> memberIds) {
        if (!hasText(clientKey)) return memberIds.values().stream().findFirst().orElse(null);
        Long resolved = memberIds.get(clientKey.trim());
        if (resolved == null) throw validation("Opening segment references a missing character member.");
        return resolved;
    }

    private static Long resolveOptionalMemberId(String clientKey, Map<String, Long> memberIds) {
        if (!hasText(clientKey)) return null;
        Long resolved = memberIds.get(clientKey.trim());
        if (resolved == null) throw validation("Worldbook entry references a missing character member.");
        return resolved;
    }

    private static String memberKey(String clientKey, int index) {
        return hasText(clientKey) ? clientKey.trim() : "member_" + index;
    }

    private static String normalizeCardType(String value) {
        return "ENSEMBLE".equalsIgnoreCase(nullToEmpty(value).trim()) ? "ENSEMBLE" : "SINGLE";
    }

    private static String normalizeSpeakerType(String value) {
        return "NARRATOR".equalsIgnoreCase(nullToEmpty(value).trim()) ? "NARRATOR" : "CHARACTER";
    }

    private static String normalizeEnum(String value, List<String> allowed, String fallback) {
        String normalized = nullToEmpty(value).trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String joinKeywords(List<String> values) {
        if (values == null) return "";
        return String.join(",", values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().limit(40).toList());
    }

    private static List<String> splitKeywords(String value) {
        if (!hasText(value)) return List.of();
        return List.of(value.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static String preview(String value) {
        String text = nullToEmpty(value).replaceAll("\\s+", " ").trim();
        return text.length() <= 80 ? text : text.substring(0, 80);
    }

    private static String clip(String value, int max) {
        String text = nullToEmpty(value).trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String firstNonBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static boolean hasText(String value) { return value != null && !value.trim().isBlank(); }
    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
