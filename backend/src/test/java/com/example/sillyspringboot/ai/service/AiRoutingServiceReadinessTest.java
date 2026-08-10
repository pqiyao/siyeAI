package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.entity.AiResolvedDeployment;
import com.example.sillyspringboot.ai.entity.AiRoute;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.ai.mapper.AiRoutingMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelProviderMapper;
import com.example.sillyspringboot.integration.sillytavern.mapper.StModelRouteMapper;
import com.example.sillyspringboot.shared.crypto.SensitiveTextCrypto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRoutingServiceReadinessTest {

    @Test
    void configuredRouteRequiresEnabledResolvedDeploymentAndDecryptableKey() {
        AiRoutingMapper mapper = mock(AiRoutingMapper.class);
        SensitiveTextCrypto crypto = mock(SensitiveTextCrypto.class);
        AiRoute route = new AiRoute();
        route.setRouteKey("chat.primary");
        route.setCapability("CHAT");
        route.setEnabled(true);
        AiResolvedDeployment deployment = new AiResolvedDeployment();
        deployment.setCapability("CHAT");
        deployment.setApiKeyCipher("cipher");
        when(mapper.findRouteByKey("chat.primary")).thenReturn(route);
        when(mapper.resolveRoute("chat.primary")).thenReturn(List.of(deployment));
        when(crypto.decrypt("cipher")).thenReturn("live-key");
        AiRoutingService service = new AiRoutingService(
                mapper,
                mock(AiProviderCatalogService.class),
                crypto,
                mock(AiRoutingRuntimeSettingsService.class),
                mock(StModelProviderMapper.class),
                mock(StModelRouteMapper.class),
                mock(AiChatModelMapper.class)
        );

        assertThat(service.isRouteConfigured("chat.primary", AiCapability.CHAT)).isTrue();
        when(crypto.decrypt("cipher")).thenReturn("");
        assertThat(service.isRouteConfigured("chat.primary", AiCapability.CHAT)).isFalse();
    }
}
