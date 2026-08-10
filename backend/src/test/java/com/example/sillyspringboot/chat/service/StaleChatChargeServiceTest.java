package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.entity.ChatGenerationContext;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleChatChargeServiceTest {

    @Test
    void scheduledReconcileDelegatesEveryStaleCharge() {
        AiChatModelMapper mapper = mock(AiChatModelMapper.class);
        H5EntitlementService entitlement = mock(H5EntitlementService.class);
        AppChatProperties properties = new AppChatProperties();
        ChatGenerationContext context = new ChatGenerationContext();
        context.setId(41L);
        when(mapper.listStaleChargeContexts(any(), eq(200))).thenReturn(List.of(context));

        new StaleChatChargeService(mapper, entitlement, properties).scheduledReconcile();

        verify(entitlement).reconcileStaleChatCharge(context);
    }

    @Test
    void scheduledCleanupDeletesOnlyThroughTerminalContextMapperContract() {
        AiChatModelMapper mapper = mock(AiChatModelMapper.class);
        H5EntitlementService entitlement = mock(H5EntitlementService.class);
        AppChatProperties properties = new AppChatProperties();
        when(mapper.deleteTerminalGenerationContexts(any(), eq(1000))).thenReturn(7);

        new StaleChatChargeService(mapper, entitlement, properties).scheduledCleanup();

        verify(mapper).deleteTerminalGenerationContexts(any(), eq(1000));
    }
}
