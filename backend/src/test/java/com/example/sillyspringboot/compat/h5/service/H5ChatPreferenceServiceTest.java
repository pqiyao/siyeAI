package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.CharacterReviewStatus;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.compat.h5.entity.AppUserChatPreference;
import com.example.sillyspringboot.compat.h5.mapper.AppUserChatPreferenceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class H5ChatPreferenceServiceTest {

    private static final long USER_ID = 7L;
    private static final long CHARACTER_ID = 11L;

    @Test
    void insertsFirstCharacterPreferenceWithExpectedRevisionZero() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(null, row(1, null, null, null));
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(null);
        when(fixture.mapper.countCharacterPreferences(USER_ID)).thenReturn(0);
        when(fixture.mapper.insert(any())).thenReturn(1);

        fixture.service.save(USER_ID, CHARACTER_ID, validBody(0));

        ArgumentCaptor<AppUserChatPreference> captor = ArgumentCaptor.forClass(AppUserChatPreference.class);
        verify(fixture.mapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getCharacterId()).isEqualTo(CHARACTER_ID);
        assertThat(captor.getValue().getBubbleJson()).contains("\"preset\":\"night\"");
    }

    @Test
    void updatesOnlyWhenExpectedRevisionMatches() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        AppUserChatPreference current = row(2, "{\"preset\":\"soft\"}", null, null);
        AppUserChatPreference updated = row(3, "{\"preset\":\"night\"}", null, null);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(current, updated);
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(null);
        when(fixture.mapper.updateIfRevision(any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);

        Map<String, Object> result = fixture.service.save(USER_ID, CHARACTER_ID, validBody(2));

        verify(fixture.mapper).updateIfRevision(any(), org.mockito.ArgumentMatchers.eq(2));
        assertThat(section(result, "character").get("revision")).isEqualTo(3);
    }

    @Test
    void acceptsV3BubbleTokens() {
        Fixture fixture = fixture();
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(null, row(1, null, null, null));
        when(fixture.mapper.insert(any())).thenReturn(1);

        Map<String, Object> body = validBody(0);
        body.put("clientSchemaVersion", 3);
        body.put("bubble", Map.ofEntries(
                Map.entry("schemaVersion", 3),
                Map.entry("presetVersion", 3),
                Map.entry("bubbleCustomized", true),
                Map.entry("customized", true),
                Map.entry("preset", "fengyue"),
                Map.entry("fontSize", 32),
                Map.entry("lineHeight", 1.7),
                Map.entry("baseFontWeight", 500),
                Map.entry("userFontWeight", 500),
                Map.entry("speechFontWeight", 600),
                Map.entry("actionFontWeight", 500),
                Map.entry("thoughtFontWeight", 500),
                Map.entry("narrationFontWeight", 500),
                Map.entry("thoughtItalic", false),
                Map.entry("surfaceMode", "flat"),
                Map.entry("surfaceBorderOpacity", 16),
                Map.entry("sideBorderWidth", 2),
                Map.entry("sideBorderOpacity", 55),
                Map.entry("shadowStrength", 10),
                Map.entry("blurRadius", 10),
                Map.entry("contentTone", "dark")
        ));

        fixture.service.save(USER_ID, null, body);

        ArgumentCaptor<AppUserChatPreference> captor = ArgumentCaptor.forClass(AppUserChatPreference.class);
        verify(fixture.mapper).insert(captor.capture());
        assertThat(captor.getValue().getBubbleJson())
                .contains("\"preset\":\"fengyue\"")
                .contains("\"surfaceMode\":\"flat\"")
                .contains("\"speechFontWeight\":600");
    }

    @Test
    void legacyClientSavePreservesV3OnlyFields() {
        Fixture fixture = fixture();
        AppUserChatPreference current = row(
                2,
                "{\"preset\":\"fengyue\",\"schemaVersion\":3,\"presetVersion\":1,\"baseFontWeight\":400,\"surfaceMode\":\"flat\",\"sideBorderWidth\":2}",
                null,
                null
        );
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(current, current);
        when(fixture.mapper.updateIfRevision(any(), org.mockito.ArgumentMatchers.eq(2))).thenReturn(1);
        Map<String, Object> body = validBody(2);
        body.put("bubble", Map.of("preset", "custom", "fontSize", 30));

        fixture.service.save(USER_ID, null, body);

        ArgumentCaptor<AppUserChatPreference> captor = ArgumentCaptor.forClass(AppUserChatPreference.class);
        verify(fixture.mapper).updateIfRevision(captor.capture(), org.mockito.ArgumentMatchers.eq(2));
        assertThat(captor.getValue().getBubbleJson())
                .contains("\"preset\":\"custom\"")
                .contains("\"fontSize\":30")
                .contains("\"schemaVersion\":3")
                .contains("\"surfaceMode\":\"flat\"")
                .contains("\"sideBorderWidth\":2");
    }

    @Test
    void rejectsStaleRevisionWithoutWriting() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(row(2, null, null, null));

        assertConflict(() -> fixture.service.save(USER_ID, CHARACTER_ID, validBody(1)));

        verify(fixture.mapper, never()).insert(any());
        verify(fixture.mapper, never()).updateIfRevision(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void convertsConcurrentFirstInsertIntoConflict() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(null);
        when(fixture.mapper.countCharacterPreferences(USER_ID)).thenReturn(0);
        when(fixture.mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertConflict(() -> fixture.service.save(USER_ID, CHARACTER_ID, validBody(0)));
    }

    @Test
    void deletesOverrideOnlyAtMatchingRevision() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID))
                .thenReturn(row(4, null, null, null), (AppUserChatPreference) null);
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(null);
        when(fixture.mapper.deleteIfRevision(USER_ID, CHARACTER_ID, 4)).thenReturn(1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedRevision", 4);
        body.put("bubble", null);
        body.put("reading", null);
        body.put("replyFormat", null);
        fixture.service.save(USER_ID, CHARACTER_ID, body);

        verify(fixture.mapper).deleteIfRevision(USER_ID, CHARACTER_ID, 4);
    }

    @Test
    void mergesSparseCharacterSectionOverGlobalSection() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.find(USER_ID, 0L)).thenReturn(row(
                3,
                "{\"fontSize\":28,\"radius\":20}",
                "{\"readMode\":\"novel\"}",
                null
        ));
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(row(
                2,
                "{\"fontSize\":31}",
                null,
                null
        ));

        Map<String, Object> result = fixture.service.load(USER_ID, CHARACTER_ID);
        Map<String, Object> bubble = section(result, "effective", "bubble");

        assertThat(bubble).containsEntry("fontSize", 31).containsEntry("radius", 20);
        assertThat(section(result, "effective", "reading")).containsEntry("readMode", "novel");
    }

    @Test
    void rejectsUnknownFieldsInvalidTypesAndOversizedBody() {
        Fixture fixture = fixture();
        Map<String, Object> unknown = validBody(0);
        unknown.put("userId", 999L);
        assertValidation(() -> fixture.service.save(USER_ID, null, unknown));

        Map<String, Object> invalidNumber = validBody(0);
        invalidNumber.put("bubble", Map.of("fontSize", 28.5));
        assertValidation(() -> fixture.service.save(USER_ID, null, invalidNumber));

        Map<String, Object> invalidColor = validBody(0);
        invalidColor.put("bubble", Map.of("baseTextColor", "#fff"));
        assertValidation(() -> fixture.service.save(USER_ID, null, invalidColor));

        Map<String, Object> invalidReadMode = validBody(0);
        invalidReadMode.put("reading", Map.of("readMode", "everything"));
        assertValidation(() -> fixture.service.save(USER_ID, null, invalidReadMode));

        Map<String, Object> invalidReplyMode = validBody(0);
        invalidReplyMode.put("replyFormat", Map.of("replySplitMode", "paragraph"));
        assertValidation(() -> fixture.service.save(USER_ID, null, invalidReplyMode));

        Map<String, Object> invalidWeight = validBody(0);
        invalidWeight.put("clientSchemaVersion", 3);
        invalidWeight.put("bubble", Map.of("baseFontWeight", 450));
        assertValidation(() -> fixture.service.save(USER_ID, null, invalidWeight));

        Map<String, Object> oversized = validBody(0);
        oversized.put("expectedRevision", new BigInteger("9".repeat(33_000)));
        assertValidation(() -> fixture.service.save(USER_ID, null, oversized));

        verifyNoInteractions(fixture.mapper, fixture.characterMapper);
    }

    @Test
    void rejectsNegativeOrInaccessibleCharacterIds() {
        Fixture fixture = fixture();
        assertValidation(() -> fixture.service.save(USER_ID, -1L, validBody(0)));

        AppCharacter privateCharacter = new AppCharacter();
        privateCharacter.setId(CHARACTER_ID);
        privateCharacter.setOwnerUserId(99L);
        privateCharacter.setPrivateCard(true);
        when(fixture.characterMapper.findById(CHARACTER_ID)).thenReturn(privateCharacter);

        assertThatThrownBy(() -> fixture.service.load(USER_ID, CHARACTER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void enforcesPerUserCharacterPreferenceLimit() {
        Fixture fixture = fixture();
        allowPublicCharacter(fixture);
        when(fixture.mapper.lockUser(USER_ID)).thenReturn(USER_ID);
        when(fixture.mapper.find(USER_ID, CHARACTER_ID)).thenReturn(null);
        when(fixture.mapper.countCharacterPreferences(USER_ID))
                .thenReturn(H5ChatPreferenceService.MAX_CHARACTER_PREFERENCES_PER_USER);

        assertValidation(() -> fixture.service.save(USER_ID, CHARACTER_ID, validBody(0)));

        verify(fixture.mapper, never()).insert(any());
    }

    private static Map<String, Object> validBody(int revision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedRevision", revision);
        body.put("bubble", Map.of(
                "bubbleCustomized", true,
                "customized", true,
                "preset", "night",
                "fontSize", 28,
                "lineHeight", 1.72,
                "baseTextColor", "#f1f5f7",
                "textColorOverrides", Map.of("speechColor", "#ffc6dc")
        ));
        body.put("reading", Map.of("readMode", "novel", "showSegmentLabels", true));
        body.put("replyFormat", Map.of("replySplitMode", "bubble"));
        return body;
    }

    private static void allowPublicCharacter(Fixture fixture) {
        AppCharacter character = new AppCharacter();
        character.setId(CHARACTER_ID);
        character.setPrivateCard(false);
        character.setClientVisible(true);
        character.setReviewStatus(CharacterReviewStatus.APPROVED);
        when(fixture.characterMapper.findById(CHARACTER_ID)).thenReturn(character);
    }

    private static AppUserChatPreference row(
            int revision,
            String bubble,
            String reading,
            String replyFormat
    ) {
        AppUserChatPreference row = new AppUserChatPreference();
        row.setUserId(USER_ID);
        row.setCharacterId(CHARACTER_ID);
        row.setRevision(revision);
        row.setBubbleJson(bubble);
        row.setReadingJson(reading);
        row.setReplyFormatJson(replyFormat);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String first, String second) {
        return (Map<String, Object>) section(root, first).get(second);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    private static void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static Fixture fixture() {
        AppUserChatPreferenceMapper mapper = mock(AppUserChatPreferenceMapper.class);
        AppCharacterMapper characterMapper = mock(AppCharacterMapper.class);
        return new Fixture(
                mapper,
                characterMapper,
                new H5ChatPreferenceService(mapper, characterMapper, new ObjectMapper())
        );
    }

    private record Fixture(
            AppUserChatPreferenceMapper mapper,
            AppCharacterMapper characterMapper,
            H5ChatPreferenceService service
    ) {}
}
