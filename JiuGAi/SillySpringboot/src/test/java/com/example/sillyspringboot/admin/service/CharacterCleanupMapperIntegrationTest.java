package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.CharacterCleanupMapper;
import com.example.sillyspringboot.character.entity.AppCharacter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CharacterCleanupMapperIntegrationTest {

    @Autowired
    private CharacterCleanupMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void locksTargetsDetectsSharedReferencesAndInvalidatesConversationResources() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long userId = insertUser(suffix);
        long targetId = insertCharacter("cleanup_" + suffix + ".png", "target");
        long otherId = insertCharacter("cleanup_" + suffix + ".png", "other");
        String assetUrl = "/uploads/h5/" + UUID.randomUUID() + ".png";
        jdbc.update("UPDATE app_character SET avatar_url = ? WHERE id = ?", assetUrl, otherId);
        jdbc.update(
                "INSERT INTO app_character_member "
                        + "(character_id, name, avatar_url, image_reference_url, voice_config_json, is_primary, sort_order, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                targetId, "member", assetUrl, assetUrl, "{\"referenceAudioUrl\":\"" + assetUrl + "\"}",
                true, 0, true
        );

        long targetConversationId = insertConversation(userId, targetId, "target conversation");
        long otherConversationId = insertConversation(userId, otherId, "other conversation");
        insertBinding(userId, targetId, targetConversationId, "cleanup_" + suffix + ".png");
        insertBinding(userId, otherId, otherConversationId, "cleanup_" + suffix + ".png");

        List<AppCharacter> locked = mapper.lockCharacters(List.of(targetId));
        assertThat(locked).singleElement().extracting(AppCharacter::getId).isEqualTo(targetId);
        assertThat(mapper.listMemberMedia(List.of(targetId))).singleElement().satisfies(row -> {
            assertThat(row.getAvatarUrl()).isEqualTo(assetUrl);
            assertThat(row.getImageReferenceUrl()).isEqualTo(assetUrl);
        });
        assertThat(mapper.countOtherCharacterStReferences(
                "cleanup_" + suffix + ".png",
                List.of(targetId)
        )).isEqualTo(1);
        assertThat(mapper.countOtherBindingStReferences(
                "cleanup_" + suffix + ".png",
                List.of(targetId)
        )).isEqualTo(1);
        assertThat(mapper.countOtherLocalAssetReferences(assetUrl, List.of(targetId))).isEqualTo(1);

        assertThat(mapper.markBindingsCharacterDeleted(List.of(targetId))).isEqualTo(1);
        assertThat(mapper.archiveConversations(List.of(targetId))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM app_conversation_st_binding WHERE conversation_id = ?",
                String.class,
                targetConversationId
        )).isEqualTo("CHARACTER_DELETED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_conversation_archive WHERE conversation_id = ?",
                Integer.class,
                targetConversationId
        )).isEqualTo(1);
    }

    private long insertUser(String suffix) {
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                Math.abs((long) suffix.hashCode()) + 9_000_000_000L,
                "cleanup_" + suffix.substring(0, 8)
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM app_user", Long.class);
    }

    private long insertCharacter(String stAvatarUrl, String name) {
        jdbc.update(
                "INSERT INTO app_character (st_avatar_url, name, description) VALUES (?, ?, ?)",
                stAvatarUrl,
                name,
                "cleanup test"
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM app_character", Long.class);
    }

    private long insertConversation(long userId, long characterId, String title) {
        jdbc.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId,
                characterId,
                title
        );
        return jdbc.queryForObject("SELECT MAX(id) FROM app_conversation", Long.class);
    }

    private void insertBinding(long userId, long characterId, long conversationId, String stAvatarUrl) {
        jdbc.update(
                "INSERT INTO app_conversation_st_binding "
                        + "(user_id, character_id, conversation_id, st_runtime_profile, st_character_ref, st_chat_ref, "
                        + "st_avatar_url, st_chat_file_name, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                characterId,
                conversationId,
                "default",
                "stchar_" + characterId,
                "chat_" + conversationId,
                stAvatarUrl,
                "chat_" + conversationId,
                "CREATED"
        );
    }
}
