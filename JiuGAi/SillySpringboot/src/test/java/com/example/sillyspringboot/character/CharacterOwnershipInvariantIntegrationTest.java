package com.example.sillyspringboot.character;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CharacterOwnershipInvariantIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppCharacterMapper characterMapper;

    @Test
    void databaseRejectsUserOwnedCharacterMarkedAsSystem() {
        long userId = insertUser();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_character (st_avatar_url, name, owner_user_id, private_card) VALUES (?, ?, ?, FALSE)",
                uniqueAvatar("owned-system"),
                "Contradictory owned character",
                userId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsOwnerlessCharacterMarkedAsPrivate() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_character (st_avatar_url, name, owner_user_id, private_card) VALUES (?, ?, NULL, TRUE)",
                uniqueAvatar("ownerless-private"),
                "Contradictory ownerless character"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvedUserCharacterNeverAppearsInPublicDiscovery() {
        long userId = insertUser();
        String avatar = uniqueAvatar("approved-private");
        jdbc.update(
                """
                INSERT INTO app_character
                    (st_avatar_url, name, owner_user_id, private_card, review_status, client_visible)
                VALUES (?, 'Approved private character', ?, TRUE, 'APPROVED', TRUE)
                """,
                avatar,
                userId
        );
        long characterId = jdbc.queryForObject(
                "SELECT id FROM app_character WHERE st_avatar_url = ?",
                Long.class,
                avatar
        );

        assertThat(characterMapper.listPublicDiscover(500))
                .extracting(AppCharacter::getId)
                .doesNotContain(characterId);
    }

    private long insertUser() {
        long telegramId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "character-invariant-" + telegramId
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
    }

    private String uniqueAvatar(String prefix) {
        return prefix + "-" + UUID.randomUUID() + ".png";
    }
}
