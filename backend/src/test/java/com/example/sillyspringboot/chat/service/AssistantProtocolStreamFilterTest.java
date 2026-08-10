package com.example.sillyspringboot.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantProtocolStreamFilterTest {

    @Test
    void hidesProtocolMarkersSplitAcrossChunksWithoutDroppingProse() {
        AssistantProtocolStreamFilter filter = AssistantProtocolStreamFilter.ensemble();

        assertThat(filter.accept("<")).isEmpty();
        assertThat(filter.accept("|sp")).isEmpty();
        assertThat(filter.accept("eaker:M1|>你好")).isEqualTo("你好");
        assertThat(filter.accept("。<|narra")).isEqualTo("。");
        assertThat(filter.accept("tor|>门开了。")).isEqualTo("门开了。");
    }

    @Test
    void preservesOrdinaryAngleBracketText() {
        AssistantProtocolStreamFilter filter = AssistantProtocolStreamFilter.ensemble();

        assertThat(filter.accept("普通<不是协议>正文")).isEqualTo("普通<不是协议>正文");
    }

    @Test
    void singleRolePassthroughNeverChangesDeltas() {
        AssistantProtocolStreamFilter filter = AssistantProtocolStreamFilter.passthrough();

        assertThat(filter.accept("<|speaker:M1|>原样正文")).isEqualTo("<|speaker:M1|>原样正文");
    }
}
