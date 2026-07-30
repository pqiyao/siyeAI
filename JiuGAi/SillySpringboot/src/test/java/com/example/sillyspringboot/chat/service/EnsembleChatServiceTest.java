package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageSegmentMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EnsembleChatServiceTest {

    private final EnsembleChatService service = new EnsembleChatService(
            mock(CharacterStudioMapper.class),
            mock(AppMessageSegmentMapper.class),
            mock(AppMessageMapper.class)
    );

    @Test
    void stableMarkersProduceIndependentCharacterAndNarratorSegments() {
        var output = service.parse(
                "<|speaker:M1|>\n你好。\n\n<|narrator|>\n门开了。\n\n<|speaker:M2|>\n进来吧。",
                members(3)
        );

        assertThat(output.segments()).extracting(EnsembleChatService.SegmentDraft::segmentType)
                .containsExactly("CHARACTER", "NARRATOR", "CHARACTER");
        assertThat(output.segments()).extracting(EnsembleChatService.SegmentDraft::memberId)
                .containsExactly(1L, null, 2L);
        assertThat(output.canonicalContent()).isEqualTo(
                "【角色1】\n你好。\n\n【旁白】\n门开了。\n\n【角色2】\n进来吧。");
    }

    @Test
    void legacyMarkersRemainCompatibleAndConsecutiveSpeakerSegmentsMerge() {
        var output = service.parse(
                "【角色1】第一句。\n\n【角色1】第二句。\n\n【角色2】回应。",
                members(2)
        );

        assertThat(output.segments()).hasSize(2);
        assertThat(output.segments().get(0).content()).isEqualTo("第一句。\n\n第二句。");
        assertThat(output.segments().get(1).memberId()).isEqualTo(2L);
    }

    @Test
    void outputWithoutMarkersFallsBackToPrimaryMember() {
        var output = service.parse("忘记输出协议但正文有效。", members(2));

        assertThat(output.segments()).hasSize(1);
        assertThat(output.segments().get(0).memberId()).isEqualTo(1L);
        assertThat(output.canonicalContent()).startsWith("【角色1】");
    }

    @Test
    void invalidTokenIsPreservedAsNarrationInsteadOfForgingPrimarySpeaker() {
        var output = service.parse("<|speaker:M9|>\n身份不可信但正文保留。", members(3));

        assertThat(output.segments()).hasSize(1);
        assertThat(output.segments().get(0).segmentType()).isEqualTo("NARRATOR");
        assertThat(output.segments().get(0).memberId()).isNull();
        assertThat(output.segments().get(0).content()).isEqualTo("身份不可信但正文保留。");
    }

    @Test
    void unmarkedPrefixBeforeValidMarkerIsNarrationInsteadOfForgedCharacterSpeech() {
        var output = service.parse(
                "门外传来脚步声。\n\n<|speaker:M2|>我回来了。",
                members(2)
        );

        assertThat(output.segments()).hasSize(2);
        assertThat(output.segments().get(0).segmentType()).isEqualTo("NARRATOR");
        assertThat(output.segments().get(0).memberId()).isNull();
        assertThat(output.segments().get(0).content()).isEqualTo("门外传来脚步声。");
        assertThat(output.segments().get(1).memberId()).isEqualTo(2L);
    }

    @Test
    void fourthDistinctSpeakerIsDowngradedToNarrationWithoutReattribution() {
        var output = service.parse(
                "<|speaker:M1|>一。<|speaker:M2|>二。<|speaker:M3|>三。<|speaker:M4|>四。",
                members(4)
        );

        assertThat(output.segments()).hasSize(4);
        assertThat(output.segments()).extracting(EnsembleChatService.SegmentDraft::memberId)
                .containsExactly(1L, 2L, 3L, null);
        assertThat(output.segments().get(3).segmentType()).isEqualTo("NARRATOR");
        assertThat(output.segments().get(3).content()).isEqualTo("四。");
    }

    @Test
    void moreThanTwentyMarkersPreserveAllTextWithinTwentySegments() {
        StringBuilder raw = new StringBuilder();
        for (int i = 1; i <= 23; i++) {
            raw.append("<|speaker:M").append((i % 2) + 1).append("|>片段").append(i).append("。");
        }

        var output = service.parse(raw.toString(), members(2));

        assertThat(output.segments()).hasSizeLessThanOrEqualTo(20);
        for (int i = 1; i <= 23; i++) {
            assertThat(output.canonicalContent()).contains("片段" + i + "。");
        }
        assertThat(output.segments().get(output.segments().size() - 1).segmentType()).isEqualTo("NARRATOR");
    }

    private static List<AppCharacterMember> members(int count) {
        List<AppCharacterMember> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            AppCharacterMember member = new AppCharacterMember();
            member.setId((long) i);
            member.setName("角色" + i);
            member.setAvatarUrl("/avatar-" + i + ".png");
            member.setPrimaryMember(i == 1);
            result.add(member);
        }
        return result;
    }
}
