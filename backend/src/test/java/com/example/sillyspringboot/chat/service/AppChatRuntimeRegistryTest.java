package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppChatRuntimeRegistryTest {

    @Test
    void staleUnregisterCannotRemoveReplacementTask() {
        AppChatRuntimeRegistry registry = new AppChatRuntimeRegistry();
        StStreamControl first = new StStreamControl();
        StStreamControl second = new StStreamControl();

        registry.register(7L, first);
        assertThat(registry.bindTask(7L, 101L, first)).isTrue();
        registry.register(7L, second);
        assertThat(first.isCancelled()).isTrue();
        assertThat(registry.bindTask(7L, 102L, second)).isTrue();

        registry.unregister(7L, first);

        assertThat(registry.cancelTask(101L)).isFalse();
        assertThat(registry.cancelTask(102L)).isTrue();
        assertThat(second.isCancelled()).isTrue();
    }

    @Test
    void taskCancellationIsScopedToTheBoundTask() {
        AppChatRuntimeRegistry registry = new AppChatRuntimeRegistry();
        StStreamControl first = new StStreamControl();
        StStreamControl second = new StStreamControl();

        registry.register(11L, first);
        registry.bindTask(11L, 201L, first);
        registry.register(12L, second);
        registry.bindTask(12L, 202L, second);

        assertThat(registry.cancelTask(201L)).isTrue();
        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isFalse();
        assertThat(registry.status().activeTasks()).isEqualTo(2);
        assertThat(registry.status().taskIds()).containsExactly(201L, 202L);
    }

    @Test
    void bindingRejectsAControlThatWasAlreadyReplaced() {
        AppChatRuntimeRegistry registry = new AppChatRuntimeRegistry();
        StStreamControl stale = new StStreamControl();
        StStreamControl current = new StStreamControl();

        registry.register(20L, stale);
        registry.register(20L, current);

        assertThat(registry.bindTask(20L, 301L, stale)).isFalse();
        assertThat(registry.bindTask(20L, 302L, current)).isTrue();
        assertThat(registry.cancelTask(301L)).isFalse();
        assertThat(registry.cancelTask(302L)).isTrue();
    }
}
