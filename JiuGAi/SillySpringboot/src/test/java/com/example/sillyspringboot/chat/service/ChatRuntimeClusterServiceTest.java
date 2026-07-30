package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRuntimeClusterServiceTest {

    @Test
    void fallsBackToLocalRuntimeWhenRedisIsMissing() {
        Fixture fixture = fixture(null);
        StStreamControl control = new StStreamControl();
        fixture.registry.register(7L, control);
        fixture.registry.bindTask(7L, 71L, control);

        ChatRuntimeClusterService.ClusterOverview overview = fixture.service.overview();
        ChatRuntimeClusterService.CancellationSignal signal = fixture.service.requestCancellation(71L);

        assertThat(overview.distributed()).isFalse();
        assertThat(overview.instanceCount()).isEqualTo(1);
        assertThat(overview.runtime().activeTasks()).isEqualTo(1);
        assertThat(signal.localSignalled()).isTrue();
        assertThat(signal.distributedAccepted()).isFalse();
        assertThat(control.isCancelled()).isTrue();
    }

    @Test
    void publishesCancellationAndConsumesItOnTheOwningNode() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zset);
        Fixture fixture = fixture(redis);
        StStreamControl control = new StStreamControl();
        fixture.registry.register(8L, control);
        fixture.registry.bindTask(8L, 81L, control);
        when(values.multiGet(any())).thenReturn(List.of("admin-node"));

        ChatRuntimeClusterService.CancellationSignal signal = fixture.service.requestCancellation(99L);
        fixture.service.synchronizeRuntime();

        assertThat(signal.localSignalled()).isFalse();
        assertThat(signal.distributedAccepted()).isTrue();
        assertThat(control.isCancelled()).isTrue();
        verify(values).set(eq("chat:runtime:cancel:99"), eq("test-node"), any(Duration.class));
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(StringRedisTemplate redis) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        AppChatProperties properties = new AppChatProperties();
        AppChatRuntimeRegistry registry = new AppChatRuntimeRegistry();
        ChatGenerationDispatcher dispatcher = new ChatGenerationDispatcher(properties);
        AppChatFrontendBridgeService bridge = new AppChatFrontendBridgeService(properties);
        ChatRuntimeClusterService service = new ChatRuntimeClusterService(
                provider,
                new ObjectMapper(),
                registry,
                dispatcher,
                bridge,
                properties,
                "test-node"
        );
        return new Fixture(service, registry);
    }

    private record Fixture(ChatRuntimeClusterService service, AppChatRuntimeRegistry registry) {
    }
}
