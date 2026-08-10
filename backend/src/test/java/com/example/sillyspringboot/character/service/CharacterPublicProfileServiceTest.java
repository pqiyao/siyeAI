package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterPublicProfileServiceTest {

    private final CharacterPublicProfileService service = new CharacterPublicProfileService();

    @Test
    void publicSummaryDoesNotFallbackToPrivateScenario() {
        AppCharacter character = new AppCharacter();
        character.setName("测试角色");
        character.setScenario("内部场景：不要公开的系统设定");
        character.setPersona("内部人设：只用于提示词");
        character.setGameplayType("沉浸式对话");

        CharacterPublicProfileService.PublicProfile profile = service.build(character);

        assertFalse(profile.publicSummary().contains("内部场景"));
        assertFalse(profile.publicSummary().contains("内部人设"));
        assertTrue(profile.healthIssues().contains("missing_public_summary"));
    }

    @Test
    void publicSummaryPrefersTaglineAndStripsTemplateResidue() {
        AppCharacter character = new AppCharacter();
        character.setTagline("{{char}} 是一位可靠的向导");
        character.setBio("较长的角色介绍");

        CharacterPublicProfileService.PublicProfile profile = service.build(character);

        assertEquals("是一位可靠的向导", profile.publicSummary());
        assertTrue(profile.publicWarnings().contains("prompt_trace_removed"));
    }

    @Test
    void relationshipHookUsesRoleRelationshipInsteadOfGameplayType() {
        String relationship = service.buildRelationshipHook(
                "舞台位于海边庄园。{{user}} 是她刚刚确认的成年伴侣。",
                "她会根据双方约定推进剧情。",
                "外冷内热，重视信任。"
        );

        assertEquals("你 是她刚刚确认的成年伴侣", relationship);
    }

    @Test
    void relationshipHookStaysEmptyWhenCardDoesNotDescribeOne() {
        String relationship = service.buildRelationshipHook(
                "故事发生在雨夜的旧城区。",
                "一名独自调查事件的记者。",
                "谨慎而敏锐。"
        );

        assertEquals("", relationship);
    }

    @Test
    void adultContentRemainsAvailableForPublicPreview() {
        String preview = service.cleanPublicSection("两名成年恋人在卧室里确认彼此的亲密关系。", 200);

        assertEquals("两名成年恋人在卧室里确认彼此的亲密关系", preview);
    }

    @Test
    void explicitMinorSexualContentIsNotPublished() {
        String preview = service.cleanPublicSection("角色今年14岁，文本包含露骨的乳房和性器描写。", 200);

        assertEquals("", preview);
    }
}
