package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.compat.h5.mapper.AppUserChatPreferenceMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class H5ChatPreferencePersistenceTest {

    private static final AtomicLong IDS = new AtomicLong(9_100_000L);

    @Autowired
    private H5ChatPreferenceService service;

    @Autowired
    private AppUserChatPreferenceMapper preferenceMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsWithOptimisticRevisionAndKeepsUsersIsolated() {
        long ownerId = insertUser();
        long otherUserId = insertUser();
        long characterId = insertPublicCharacter();

        Map<String, Object> first = service.save(ownerId, characterId, body(0, 29));
        assertThat(scope(first, "character").get("revision")).isEqualTo(1);

        assertThatThrownBy(() -> service.save(ownerId, characterId, body(0, 31)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        Map<String, Object> second = service.save(ownerId, characterId, body(1, 31));
        assertThat(scope(second, "character").get("revision")).isEqualTo(2);
        assertThat(scope(second, "effective", "bubble")).containsEntry("fontSize", 31);

        Map<String, Object> isolated = service.load(otherUserId, characterId);
        assertThat(scope(isolated, "global").get("revision")).isEqualTo(0);
        assertThat(scope(isolated, "character").get("revision")).isEqualTo(0);
        assertThat(scope(isolated, "effective", "bubble")).isEmpty();
    }

    @Test
    void characterPreferenceLimitCountsOnlyAccessibleActiveCharacters() {
        long ownerId = insertUser();
        long otherUserId = insertUser();

        long publicCharacterId = insertCharacter(null, false, "APPROVED", true, false);
        long ownedCharacterId = insertCharacter(ownerId, true, "APPROVED", true, false);
        long deletedCharacterId = insertCharacter(null, false, "APPROVED", true, true);
        long hiddenCharacterId = insertCharacter(null, false, "APPROVED", false, false);
        long rejectedCharacterId = insertCharacter(null, false, "REJECTED", true, false);
        long otherPrivateCharacterId = insertCharacter(otherUserId, true, "APPROVED", true, false);

        insertPreference(ownerId, publicCharacterId);
        insertPreference(ownerId, ownedCharacterId);
        insertPreference(ownerId, deletedCharacterId);
        insertPreference(ownerId, hiddenCharacterId);
        insertPreference(ownerId, rejectedCharacterId);
        insertPreference(ownerId, otherPrivateCharacterId);
        insertPreference(ownerId, IDS.incrementAndGet());

        assertThat(preferenceMapper.countCharacterPreferences(ownerId)).isEqualTo(2);
    }

    private long insertUser() {
        long telegramId = IDS.incrementAndGet();
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "preference-test-" + telegramId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
    }

    private long insertPublicCharacter() {
        return insertCharacter(null, false, "APPROVED", true, false);
    }

    private long insertCharacter(
            Long ownerUserId,
            boolean privateCard,
            String reviewStatus,
            boolean clientVisible,
            boolean deleted
    ) {
        String avatar = "preference-test-" + IDS.incrementAndGet() + ".png";
        jdbc.update(
                """
                INSERT INTO app_character
                    (st_avatar_url, name, private_card, owner_user_id, review_status, client_visible,
                     unlocked_default, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?)
                """,
                avatar,
                "Preference Test",
                privateCard,
                ownerUserId,
                reviewStatus,
                clientVisible,
                deleted ? LocalDateTime.now() : null
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_character WHERE st_avatar_url = ?",
                Long.class,
                avatar
        );
    }

    private void insertPreference(long userId, long characterId) {
        jdbc.update(
                """
                INSERT INTO app_user_chat_preference
                    (user_id, character_id, bubble_json, reading_json, reply_format_json, revision)
                VALUES (?, ?, '{}', NULL, NULL, 1)
                """,
                userId,
                characterId
        );
    }

    private static Map<String, Object> body(int revision, int fontSize) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("expectedRevision", revision);
        body.put("bubble", Map.of(
                "bubbleCustomized", true,
                "customized", true,
                "preset", "custom",
                "fontSize", fontSize,
                "lineHeight", 1.7,
                "baseTextColor", "#ffffff"
        ));
        body.put("reading", Map.of("readMode", "original", "showSegmentLabels", false));
        body.put("replyFormat", Map.of("replySplitMode", "none"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scope(Map<String, Object> root, String key) {
        return (Map<String, Object>) root.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> scope(Map<String, Object> root, String first, String second) {
        return (Map<String, Object>) scope(root, first).get(second);
    }
}
