package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.chat.entity.AppMessageSegment;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageSegmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnsembleChatService {
    public static final String MODE_NATURAL = "NATURAL";
    public static final String MODE_STORY = "STORY";

    private static final int MAX_SEGMENTS = 20;
    private static final Pattern STABLE_MARKER = Pattern.compile(
            "<\\|(?:speaker:([^|>]+)|narrator)\\|>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_MARKER = Pattern.compile(
            "(?m)^[ \\t]*【([^】\\r\\n]{1,64})】[ \\t]*");

    private final CharacterStudioMapper studioMapper;
    private final AppMessageSegmentMapper segmentMapper;
    private final AppMessageMapper messageMapper;

    public EnsembleChatService(
            CharacterStudioMapper studioMapper,
            AppMessageSegmentMapper segmentMapper,
            AppMessageMapper messageMapper
    ) {
        this.studioMapper = studioMapper;
        this.segmentMapper = segmentMapper;
        this.messageMapper = messageMapper;
    }

    public EnsembleContext loadContext(long characterId) {
        String cardType = normalize(studioMapper.findCardType(characterId));
        List<AppCharacterMember> members = studioMapper.listMembers(characterId);
        if (!"ENSEMBLE".equals(cardType) || members == null || members.size() < 2) {
            return EnsembleContext.disabled(characterId);
        }
        return new EnsembleContext(
                characterId,
                true,
                normalizeMode(studioMapper.findEnsembleChatMode(characterId)),
                List.copyOf(members)
        );
    }

    public String buildRuntimePrompt(long characterId, String planningConstraint) {
        EnsembleContext context = loadContext(characterId);
        if (!context.enabled()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("Ensemble roleplay protocol (mandatory):\n")
                .append("- This card contains independent characters. Keep each personality, knowledge boundary, goal and relationship distinct.\n")
                .append("- Choose only characters naturally present and relevant to this turn. Never speak for the human user.\n")
                .append("- Use 1 to 3 distinct speaking characters. One speaker is correct when nobody else needs to respond.\n")
                .append("- Begin every segment with exactly one registered marker from the roster below.\n")
                .append("- Use <|narrator|> only for useful scene narration. Do not print display names as headers.\n")
                .append("- Never invent, rename or explain these markers. Output roleplay prose only.\n")
                .append("Registered roster:\n");
        for (int i = 0; i < context.members().size(); i++) {
            AppCharacterMember member = context.members().get(i);
            prompt.append("- <|speaker:M").append(i + 1).append("|> = ")
                    .append(EnsemblePromptText.sanitize(member.getName()))
                    .append(Boolean.TRUE.equals(member.getPrimaryMember()) ? " (primary)" : "")
                    .append('\n');
            if (StringUtils.hasText(member.getTagline())) {
                prompt.append("  Role: ").append(EnsemblePromptText.sanitize(member.getTagline())).append('\n');
            }
            if (StringUtils.hasText(member.getPersona())) {
                prompt.append("  Persona: ").append(EnsemblePromptText.sanitize(member.getPersona())).append('\n');
            }
        }
        if (StringUtils.hasText(planningConstraint)) {
            prompt.append("Story-mode speaker plan for this turn (constraints only; write the actual reply yourself):\n")
                    .append(planningConstraint.trim()).append('\n');
        }
        return prompt.toString().trim();
    }

    public PreparedOutput prepare(long characterId, String rawContent) {
        EnsembleContext context = loadContext(characterId);
        if (!context.enabled()) {
            return PreparedOutput.passthrough(rawContent);
        }
        return parse(rawContent, context.members());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(long messageId, PreparedOutput output) {
        if (output == null || !output.ensemble()) {
            return;
        }
        segmentMapper.deleteByMessageId(messageId);
        persistDrafts(messageId, output.segments());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateVariant(
            long characterId,
            long sourceMessageId,
            long targetMessageId,
            String sourceContent
    ) {
        activateVariantData(characterId, sourceMessageId, targetMessageId, sourceContent);
    }

    public void activateVariantInCurrentTransaction(
            long characterId,
            long sourceMessageId,
            long targetMessageId,
            String sourceContent
    ) {
        activateVariantData(characterId, sourceMessageId, targetMessageId, sourceContent);
    }

    private void activateVariantData(
            long characterId,
            long sourceMessageId,
            long targetMessageId,
            String sourceContent
    ) {
        if (targetMessageId <= 0) {
            return;
        }
        List<AppMessageSegment> source = sourceMessageId > 0
                ? segmentMapper.listByMessageId(sourceMessageId)
                : List.of();
        PreparedOutput prepared = source.isEmpty() ? prepare(characterId, sourceContent) : null;
        segmentMapper.deleteByMessageId(targetMessageId);
        if (!source.isEmpty()) {
            int index = 0;
            for (AppMessageSegment item : source) {
                insertSegment(
                        targetMessageId,
                        index++,
                        item.getSegmentType(),
                        item.getSpeakerMemberId(),
                        item.getSpeakerNameSnapshot(),
                        item.getSpeakerAvatarSnapshot(),
                        item.getContent()
                );
            }
            updateFirstSpeaker(targetMessageId, source);
            return;
        }
        if (prepared == null || !prepared.ensemble()) {
            return;
        }
        persistDrafts(targetMessageId, prepared.segments());
    }

    public PreparedOutput parse(String rawContent, List<AppCharacterMember> members) {
        String raw = rawContent == null ? "" : rawContent.trim();
        if (members == null || members.size() < 2) {
            return PreparedOutput.passthrough(raw);
        }
        List<Marker> markers = stableMarkers(raw, members);
        if (markers.isEmpty()) {
            markers = legacyMarkers(raw, members);
        }
        List<SegmentDraft> segments = markers.isEmpty()
                ? fallbackSegments(raw, members)
                : buildSegments(raw, markers, members);
        if (segments.isEmpty()) {
            segments = fallbackSegments(raw, members);
        }
        return new PreparedOutput(canonicalContent(segments), List.copyOf(segments), true);
    }

    public List<Map<String, Object>> segmentMaps(long messageId) {
        if (messageId <= 0) {
            return List.of();
        }
        return segmentMapper.listByMessageId(messageId).stream().map(this::segmentMap).toList();
    }

    public Map<Long, List<Map<String, Object>>> segmentMapsByMessageIds(List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (AppMessageSegment segment : segmentMapper.listByMessageIds(messageIds)) {
            result.computeIfAbsent(segment.getMessageId(), ignored -> new ArrayList<>()).add(segmentMap(segment));
        }
        return result;
    }

    private List<Marker> stableMarkers(String raw, List<AppCharacterMember> members) {
        List<Marker> result = new ArrayList<>();
        LinkedHashMap<Long, AppCharacterMember> acceptedSpeakers = new LinkedHashMap<>();
        Matcher matcher = STABLE_MARKER.matcher(raw);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) {
                result.add(new Marker(matcher.start(), matcher.end(), null, true));
                continue;
            }
            int memberIndex = parseMemberToken(token);
            AppCharacterMember member = memberIndex >= 0 && memberIndex < members.size()
                    ? members.get(memberIndex)
                    : null;
            member = acceptSpeaker(member, acceptedSpeakers);
            result.add(new Marker(matcher.start(), matcher.end(), member, member == null));
        }
        return result;
    }

    private List<Marker> legacyMarkers(String raw, List<AppCharacterMember> members) {
        List<Marker> result = new ArrayList<>();
        LinkedHashMap<Long, AppCharacterMember> acceptedSpeakers = new LinkedHashMap<>();
        Matcher matcher = LEGACY_MARKER.matcher(raw);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            boolean narrator = "旁白".equalsIgnoreCase(name) || "narrator".equalsIgnoreCase(name);
            AppCharacterMember member = narrator ? null : findMemberByName(members, name);
            if (narrator || member != null) {
                if (member != null) {
                    member = acceptSpeaker(member, acceptedSpeakers);
                }
                result.add(new Marker(matcher.start(), matcher.end(), member, narrator || member == null));
            }
        }
        return result;
    }

    private List<SegmentDraft> buildSegments(String raw, List<Marker> markers, List<AppCharacterMember> members) {
        List<SegmentDraft> result = new ArrayList<>();
        String prefix = raw.substring(0, markers.get(0).start()).trim();
        if (!prefix.isBlank()) {
            addMerged(result, draftFor(null, true, prefix));
        }
        int markerLimit = Math.min(markers.size(), MAX_SEGMENTS);
        for (int i = 0; i < markerLimit; i++) {
            Marker marker = markers.get(i);
            int end = i + 1 < markers.size() ? markers.get(i + 1).start() : raw.length();
            String content = raw.substring(marker.end(), end).trim();
            if (!content.isBlank()) {
                addMerged(result, draftFor(marker.member(), marker.narrator(), content));
            }
        }
        if (markers.size() > markerLimit && !result.isEmpty()) {
            StringBuilder overflow = new StringBuilder();
            for (int i = markerLimit; i < markers.size(); i++) {
                Marker marker = markers.get(i);
                int end = i + 1 < markers.size() ? markers.get(i + 1).start() : raw.length();
                String content = raw.substring(marker.end(), end).trim();
                if (!content.isBlank()) {
                    if (overflow.length() > 0) overflow.append("\n\n");
                    overflow.append(content);
                }
            }
            if (overflow.length() > 0) {
                addMerged(result, draftFor(null, true, overflow.toString()));
                if (result.size() > MAX_SEGMENTS) {
                    SegmentDraft overflowNarration = result.remove(result.size() - 1);
                    SegmentDraft tail = result.get(result.size() - 1);
                    if ("NARRATOR".equals(tail.segmentType())) {
                        result.set(result.size() - 1, tail.withContent(
                                tail.content() + "\n\n" + overflowNarration.content()));
                    } else {
                        result.set(result.size() - 1, draftFor(
                                null, true, tail.content() + "\n\n" + overflowNarration.content()));
                    }
                }
            }
        }
        return result;
    }

    private void persistDrafts(long messageId, List<SegmentDraft> drafts) {
        int index = 0;
        for (SegmentDraft draft : drafts) {
            insertSegment(
                    messageId,
                    index++,
                    draft.segmentType(),
                    draft.memberId(),
                    draft.speakerName(),
                    draft.speakerAvatarUrl(),
                    draft.content()
            );
        }
        SegmentDraft first = drafts.stream().filter(item -> item.memberId() != null).findFirst().orElse(null);
        if (first != null) {
            messageMapper.updateSpeakerSnapshot(
                    messageId, first.memberId(), first.speakerName(), first.speakerAvatarUrl());
        }
    }

    private void insertSegment(
            long messageId,
            int index,
            String type,
            Long memberId,
            String name,
            String avatar,
            String content
    ) {
        AppMessageSegment segment = new AppMessageSegment();
        segment.setMessageId(messageId);
        segment.setSegmentIndex(index);
        segment.setSegmentType(type);
        segment.setSpeakerMemberId(memberId);
        segment.setSpeakerNameSnapshot(name);
        segment.setSpeakerAvatarSnapshot(avatar);
        segment.setContent(content);
        segment.setStatus("SUCCESS");
        segmentMapper.insert(segment);
    }

    private void updateFirstSpeaker(long messageId, List<AppMessageSegment> source) {
        AppMessageSegment first = source.stream()
                .filter(item -> item.getSpeakerMemberId() != null)
                .findFirst()
                .orElse(null);
        if (first != null) {
            messageMapper.updateSpeakerSnapshot(
                    messageId,
                    first.getSpeakerMemberId(),
                    first.getSpeakerNameSnapshot(),
                    first.getSpeakerAvatarSnapshot()
            );
        }
    }

    private static List<SegmentDraft> fallbackSegments(String raw, List<AppCharacterMember> members) {
        if (raw.isBlank()) {
            return List.of();
        }
        return List.of(draftFor(primaryMember(members), false, raw));
    }

    private static void addMerged(List<SegmentDraft> target, SegmentDraft next) {
        if (target.isEmpty()) {
            target.add(next);
            return;
        }
        SegmentDraft previous = target.get(target.size() - 1);
        if (previous.sameSpeaker(next)) {
            target.set(target.size() - 1, previous.withContent(previous.content() + "\n\n" + next.content()));
        } else {
            target.add(next);
        }
    }

    private static SegmentDraft draftFor(AppCharacterMember member, boolean narrator, String content) {
        if (narrator || member == null) {
            return new SegmentDraft("NARRATOR", null, "旁白", "", content.trim());
        }
        return new SegmentDraft(
                "CHARACTER",
                member.getId(),
                member.getName() == null ? "" : member.getName().trim(),
                member.getAvatarUrl() == null ? "" : member.getAvatarUrl().trim(),
                content.trim()
        );
    }

    private static String canonicalContent(List<SegmentDraft> segments) {
        StringBuilder result = new StringBuilder();
        for (SegmentDraft segment : segments) {
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append('【').append(segment.speakerName()).append("】\n").append(segment.content());
        }
        return result.toString().trim();
    }

    private Map<String, Object> segmentMap(AppMessageSegment segment) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", segment.getSegmentIndex());
        item.put("type", segment.getSegmentType());
        item.put("speakerMemberId", segment.getSpeakerMemberId());
        item.put("speakerName", value(segment.getSpeakerNameSnapshot()));
        item.put("speakerAvatarUrl", value(segment.getSpeakerAvatarSnapshot()));
        item.put("content", value(segment.getContent()));
        return item;
    }

    private static int parseMemberToken(String token) {
        String normalized = normalize(token);
        if (!normalized.matches("M[1-8]")) {
            return -1;
        }
        return Integer.parseInt(normalized.substring(1)) - 1;
    }

    private static AppCharacterMember primaryMember(List<AppCharacterMember> members) {
        return members.stream()
                .filter(item -> Boolean.TRUE.equals(item.getPrimaryMember()))
                .findFirst()
                .orElse(members.get(0));
    }

    private static AppCharacterMember acceptSpeaker(
            AppCharacterMember member,
            LinkedHashMap<Long, AppCharacterMember> accepted
    ) {
        if (member == null || member.getId() == null) {
            return null;
        }
        AppCharacterMember existing = accepted.get(member.getId());
        if (existing != null) {
            return existing;
        }
        if (accepted.size() >= 3) {
            return null;
        }
        accepted.put(member.getId(), member);
        return member;
    }

    private static AppCharacterMember findMemberByName(List<AppCharacterMember> members, String name) {
        for (AppCharacterMember member : members) {
            if (member.getName() != null && member.getName().trim().equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    private static String normalizeMode(String value) {
        return MODE_STORY.equals(normalize(value)) ? MODE_STORY : MODE_NATURAL;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record EnsembleContext(long characterId, boolean enabled, String mode, List<AppCharacterMember> members) {
        static EnsembleContext disabled(long characterId) {
            return new EnsembleContext(characterId, false, MODE_NATURAL, List.of());
        }
    }

    public record PreparedOutput(String canonicalContent, List<SegmentDraft> segments, boolean ensemble) {
        public static PreparedOutput passthrough(String content) {
            return new PreparedOutput(content == null ? "" : content.trim(), List.of(), false);
        }
    }

    public record SegmentDraft(
            String segmentType,
            Long memberId,
            String speakerName,
            String speakerAvatarUrl,
            String content
    ) {
        boolean sameSpeaker(SegmentDraft other) {
            return segmentType.equals(other.segmentType) && Objects.equals(memberId, other.memberId);
        }

        SegmentDraft withContent(String nextContent) {
            return new SegmentDraft(segmentType, memberId, speakerName, speakerAvatarUrl, nextContent.trim());
        }
    }

    private record Marker(int start, int end, AppCharacterMember member, boolean narrator) {
    }
}
