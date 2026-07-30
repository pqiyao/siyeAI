package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.MessageSemanticAnnotationProperties;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.entity.AppMessageSemanticAnnotation;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageSemanticAnnotationMapper;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MessageSemanticAnnotationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesLosslessUtf16SegmentsAndDerivesOffsets() {
        String content = "她抬起头。\n“欢迎回来😀。”（其实还有些紧张。）";
        String raw = """
                {"segments":[
                  {"type":"action","text":"她抬起头。\\n","confidence":0.98},
                  {"type":"speech","text":"“欢迎回来😀。”","confidence":0.97},
                  {"type":"thought","text":"（其实还有些紧张。）","confidence":0.91}
                ]}
                """;

        var result = MessageSemanticAnnotationService.validateClassifierPayload(content, raw, objectMapper);

        assertNotNull(result);
        assertEquals(3, result.segments().size());
        assertEquals(content.length(), result.segments().get(2).end());
        assertEquals("speech", result.segments().get(1).type());
    }

    @Test
    void rejectsAnyRewriteOmissionOrUnsupportedType() {
        String content = "她抬头。“你好。”";
        assertNull(MessageSemanticAnnotationService.validateClassifierPayload(
                content,
                "{\"segments\":[{\"type\":\"action\",\"text\":\"她抬头。\"},{\"type\":\"speech\",\"text\":\"你好。\"}]}",
                objectMapper
        ));
        assertNull(MessageSemanticAnnotationService.validateClassifierPayload(
                content,
                "{\"segments\":[{\"type\":\"emotion\",\"text\":\"" + content + "\"}]}",
                objectMapper
        ));
    }

    @Test
    void rejectsOffsetsThatWouldOnlyMatchNormalizedText() {
        String content = "第一行\r\n第二行";
        String normalized = "{\"segments\":[{\"type\":\"narration\",\"text\":\"第一行\\n第二行\"}]}";

        assertNull(MessageSemanticAnnotationService.validateClassifierPayload(content, normalized, objectMapper));
    }

    @Test
    void fingerprintIsStableOverUtf16CodeUnits() {
        assertEquals("811c9dc5", MessageSemanticAnnotationService.textFingerprint(""));
        assertEquals(MessageSemanticAnnotationService.textFingerprint("你好😀"),
                MessageSemanticAnnotationService.textFingerprint("你好😀"));
    }

    @Test
    void defaultDisabledSettingNeverCallsClassifier() {
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        when(featureSettingsService.getSettings()).thenReturn(new AppFeatureSettings());
        StClient stClient = mock(StClient.class);
        MessageSemanticAnnotationService service = new MessageSemanticAnnotationService(
                mock(AppMessageMapper.class), mock(AppMessageSemanticAnnotationMapper.class),
                new MessageSemanticAnnotationProperties(), stClient, featureSettingsService,
                mock(AiRoutingService.class), objectMapper
        );
        try {
            service.triggerAfterCommit(7L);
            verify(stClient, never()).streamChatCompletionsGenerate(any(), any(), any());
        } finally {
            service.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledSettingUsesOnlyTheDedicatedChatRoute() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppMessageSemanticAnnotationMapper annotationMapper = mock(AppMessageSemanticAnnotationMapper.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setSemanticAnnotationEnabled(true);
        settings.setSemanticAnnotationRouteKey("chat.semantic-cheap");
        when(featureSettingsService.getSettings()).thenReturn(settings);
        AiRoutingService routingService = mock(AiRoutingService.class);
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);
        when(routingService.isRouteConfigured("chat.semantic-cheap", AiCapability.CHAT)).thenReturn(true);

        AppMessage message = new AppMessage();
        message.setId(7L);
        message.setConversationId(11L);
        message.setRole("assistant");
        message.setStatus("SUCCESS");
        message.setContent("你好");
        when(messageMapper.findById(7L)).thenReturn(message);

        StClient stClient = mock(StClient.class);
        doAnswer(invocation -> {
            Consumer<ChatGenerateChunk> consumer = invocation.getArgument(1);
            consumer.accept(new ChatGenerateChunk(11L, "7", 0,
                    "{\"segments\":[{\"type\":\"speech\",\"text\":\"你好\",\"confidence\":1.0}]}",
                    true, null, null));
            return null;
        }).when(stClient).streamChatCompletionsGenerate(any(), any(Consumer.class), any());

        MessageSemanticAnnotationService service = new MessageSemanticAnnotationService(
                messageMapper, annotationMapper, new MessageSemanticAnnotationProperties(), stClient,
                featureSettingsService, routingService, objectMapper
        );
        try {
            service.triggerAfterCommit(7L);
            var captor = org.mockito.ArgumentCaptor.forClass(ChatGenerateRequest.class);
            verify(stClient, timeout(1000).times(1))
                    .streamChatCompletionsGenerate(captor.capture(), any(), any());
            assertEquals("chat.semantic-cheap", captor.getValue().routingRouteKey());
            assertEquals(AiCapability.CHAT, captor.getValue().routingCapabilityOrChat());
        } finally {
            service.shutdown();
        }
    }

    @Test
    void neverPublishesReadyAnnotationFromAnOlderClassifierVersion() {
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppMessageSemanticAnnotationMapper annotationMapper = mock(AppMessageSemanticAnnotationMapper.class);
        MessageSemanticAnnotationProperties properties = new MessageSemanticAnnotationProperties();
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings featureSettings = new AppFeatureSettings();
        featureSettings.setSemanticAnnotationEnabled(true);
        featureSettings.setSemanticAnnotationRouteKey("chat.semantic-cheap");
        when(featureSettingsService.getSettings()).thenReturn(featureSettings);
        AiRoutingService routingService = mock(AiRoutingService.class);
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);
        when(routingService.isRouteConfigured("chat.semantic-cheap", AiCapability.CHAT))
                .thenReturn(true);
        MessageSemanticAnnotationService service = new MessageSemanticAnnotationService(
                messageMapper, annotationMapper, properties, mock(StClient.class),
                featureSettingsService, routingService, objectMapper
        );
        try {
            AppMessage message = new AppMessage();
            message.setId(7L);
            message.setRole("assistant");
            message.setStatus("SUCCESS");
            message.setContent("“欢迎回来。”");

            AppMessageSemanticAnnotation stale = new AppMessageSemanticAnnotation();
            stale.setMessageId(7L);
            stale.setContentHash(MessageSemanticAnnotationService.hash(message.getContent()));
            stale.setSchemaVersion(MessageSemanticAnnotationService.SCHEMA_VERSION);
            stale.setClassifierVersion("roleplay-semantic-older");
            stale.setStatus("READY");
            stale.setSegmentsJson("[{\"type\":\"speech\",\"start\":0,\"end\":7,\"confidence\":0.9}]");
            when(annotationMapper.listReadyByMessageIds(List.of(7L))).thenReturn(List.of(stale));

            assertTrue(service.readyAnnotationsForMessages(List.of(message)).isEmpty());
        } finally {
            service.shutdown();
        }
    }
}
