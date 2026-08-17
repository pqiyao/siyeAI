package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.entity.AiChatModelSettings;
import com.example.sillyspringboot.ai.entity.AiChatOffering;
import com.example.sillyspringboot.ai.entity.AiChatOfferingPrice;
import com.example.sillyspringboot.ai.entity.AiRoute;
import com.example.sillyspringboot.ai.entity.AiRouteMember;
import com.example.sillyspringboot.ai.entity.UserAiChatModel;
import com.example.sillyspringboot.ai.mapper.AiChatModelMapper;
import com.example.sillyspringboot.ai.mapper.AiRoutingMapper;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatModelServiceTest {

    private final AiChatModelMapper mapper = mock(AiChatModelMapper.class);
    private final AiRoutingMapper routingMapper = mock(AiRoutingMapper.class);
    private final AiRoutingService routingService = mock(AiRoutingService.class);
    private final EntitlementPolicyService policyService = mock(EntitlementPolicyService.class);
    private final H5UserAiProviderService userAiProviderService = mock(H5UserAiProviderService.class);
    private AiChatModelService service;

    @BeforeEach
    void setUp() {
        service = new AiChatModelService(
                mapper,
                routingMapper,
                routingService,
                mock(AppConversationMapper.class),
                mock(AppH5UserProfileExtMapper.class),
                policyService,
                userAiProviderService
        );
        AiChatModelSettings settings = new AiChatModelSettings();
        settings.setId(1L);
        settings.setEnabled(true);
        settings.setCanaryPercent(100);
        settings.setShadowEnabled(true);
        when(mapper.findSettings()).thenReturn(settings);
        when(policyService.effectiveVipLevel(any())).thenReturn(0);
        when(userAiProviderService.isCustomModeAllowedForUser(anyLong())).thenReturn(true);
    }

    @Test
    void explicitMissingByokModelNeverFallsBackToDefaultOrOfficial() {
        when(mapper.findUserModel(7L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveForGeneration(7L, 0L, "BYOK", "999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自定义 API");

        verify(mapper, never()).findDefaultUserModel(7L);
        verify(mapper, never()).findDefaultOffering();
    }

    @Test
    void explicitMissingPlatformOfferingNeverSilentlyChangesModel() {
        when(mapper.findOfferingByCode("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.resolveForGeneration(7L, 0L, "SYSTEM", "missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或已下架");

        verify(mapper, never()).findDefaultOffering();
    }

    @Test
    void firstRolloutKeepsLegacyByokModeInsteadOfSwitchingToOfficial() {
        UserAiChatModel model = new UserAiChatModel();
        model.setId(31L);
        model.setUserId(7L);
        model.setModelName("legacy-chat-model");
        model.setEnabled(true);
        model.setDefaultModel(true);

        when(userAiProviderService.isCustomModeSelectedForUser(7L)).thenReturn(true);
        when(userAiProviderService.resolveActiveOverrideForUser(7L)).thenReturn(mock(UserModelOverride.class));
        when(mapper.findDefaultUserModel(7L)).thenReturn(model);

        AiChatModelService.ResolvedChatModel resolved =
                service.resolveForGeneration(7L, 0L, "", "");

        assertThat(resolved.byok()).isTrue();
        assertThat(resolved.modelName()).isEqualTo("legacy-chat-model");
        verify(mapper, never()).findDefaultOffering();
    }

    @Test
    void administrativelyDisabledByokFallsBackToOfficialDefault() {
        AiChatOffering offering = availableOffering();
        AiChatOfferingPrice price = freePrice();
        AiRoute route = availableRoute();

        when(userAiProviderService.isCustomModeAllowedForUser(7L)).thenReturn(false);
        when(mapper.findDefaultOffering()).thenReturn(offering);
        when(mapper.listPrices(11L)).thenReturn(List.of(price));
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);
        when(routingMapper.listRouteMembers(21L)).thenReturn(List.of(new AiRouteMember()));
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);

        AiChatModelService.ResolvedChatModel resolved =
                service.resolveForGeneration(7L, 0L, "BYOK", "31");

        assertThat(resolved.platformOffering()).isTrue();
        assertThat(resolved.offeringCode()).isEqualTo("story");
        verify(userAiProviderService, never()).resolveActiveOverrideForUser(7L);
    }

    @Test
    void changedPlatformQuoteIsRejectedBeforeGeneration() {
        AiChatOffering offering = availableOffering();
        offering.setVersionNo(4L);
        AiRoute route = availableRoute();

        when(mapper.findOfferingByCode("story")).thenReturn(offering);
        when(mapper.listPrices(11L)).thenReturn(List.of(freePrice()));
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);
        when(routingMapper.listRouteMembers(21L)).thenReturn(List.of(new AiRouteMember()));
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);

        assertThatThrownBy(() -> service.resolveForGeneration(7L, 0L, "SYSTEM", "story", 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格或配置已更新");
    }

    @Test
    void enablingCanaryRejectsDefaultOfferingWithDisabledRoute() {
        AiChatOffering offering = new AiChatOffering();
        offering.setRouteKey("chat.offer.story");
        AiRoute route = new AiRoute();
        route.setId(21L);
        route.setCapability("CHAT");
        route.setEnabled(false);

        when(mapper.findDefaultOffering()).thenReturn(offering);
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);

        assertThatThrownBy(() -> service.saveSettings(Map.of(
                "enabled", true,
                "shadowEnabled", true,
                "canaryPercent", 5
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("路由可用");

        verify(mapper, never()).updateSettings(any());
    }

    @Test
    void enablingCanaryRejectsDisabledChatRuntime() {
        AiChatOffering offering = availableOffering();
        when(mapper.findDefaultOffering()).thenReturn(offering);
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(false);
        when(routingService.isRouteConfigured("chat.offer.story", AiCapability.CHAT)).thenReturn(true);

        assertThatThrownBy(() -> service.saveSettings(Map.of(
                "enabled", true,
                "canaryPercent", 100
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认聊天模型");

        verify(mapper, never()).updateSettings(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void catalogMarksDisabledRouteUnavailableInsteadOfThrowing() {
        AiChatOffering offering = new AiChatOffering();
        offering.setId(11L);
        offering.setOfferingCode("story");
        offering.setDisplayName("剧情模型");
        offering.setRouteKey("chat.offer.story");
        offering.setEnabled(true);
        offering.setMaintenance(false);
        offering.setVipMinLevel(0);
        offering.setSpeedLevel(3);
        offering.setQualityLevel(4);

        AiChatOfferingPrice price = new AiChatOfferingPrice();
        price.setOfferingId(11L);
        price.setVipLevel(0);
        price.setBillingMode("DIAMOND_ONLY");
        price.setDiamondCost(2);

        AiRoute route = new AiRoute();
        route.setId(21L);
        route.setCapability("CHAT");
        route.setEnabled(false);

        when(mapper.listPublishedOfferings()).thenReturn(List.of(offering));
        when(mapper.listPrices(11L)).thenReturn(List.of(price));
        when(mapper.listUserModels(7L)).thenReturn(List.of());
        when(mapper.findDefaultOffering()).thenReturn(null);
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);
        when(routingService.isCapabilityEnabled(any())).thenReturn(true);

        Map<String, Object> catalog = service.userCatalog(7L, null);
        List<Map<String, Object>> models = (List<Map<String, Object>>) catalog.get("platformModels");

        assertThat(models).hasSize(1);
        assertThat(models.get(0))
                .containsEntry("available", false)
                .containsEntry("unavailableReason", "模型线路暂不可用")
                .containsEntry("priceText", "2钻石/次");
    }

    @Test
    void liveRolloutRejectsDisablingTheOnlyDefaultOffering() {
        AiChatOffering offering = availableOffering();
        AiRoute route = availableRoute();
        when(mapper.findOfferingById(11L)).thenReturn(offering);
        when(mapper.updateOffering(any())).thenReturn(1);
        when(mapper.findDefaultOffering()).thenReturn(null);
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);

        Map<String, Object> body = new HashMap<>();
        body.put("id", 11L);
        body.put("displayName", "剧情模型");
        body.put("routeKey", "chat.offer.story");
        body.put("enabled", false);
        body.put("maintenance", false);
        body.put("defaultOffering", true);
        body.put("prices", List.of(Map.of(
                "vipLevel", 0,
                "billingMode", "FREE",
                "quotaUnits", 0,
                "diamondCost", 0,
                "goldCost", 0
        )));

        assertThatThrownBy(() -> service.saveOffering(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须保留一个已发布、非维护且路由可用的默认聊天模型");
    }

    @Test
    void deletingDisabledOfferingReclaimsItsUnusedDedicatedRoute() {
        AiChatOffering offering = availableOffering();
        offering.setEnabled(false);
        AiRoute route = availableRoute();
        route.setRouteKey("chat.offer.story");

        when(mapper.findOfferingById(11L)).thenReturn(offering);
        when(mapper.findDefaultOffering()).thenReturn(availableOffering());
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);
        when(routingService.isRouteConfigured("chat.offer.story", AiCapability.CHAT)).thenReturn(true);
        when(mapper.listOfferings()).thenReturn(List.of());
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);

        service.deleteOffering(11L);

        verify(mapper).deletePrices(11L);
        verify(mapper).deleteOffering(11L);
        verify(routingService).deleteRoute(21L);
    }

    @Test
    void deletingOfferingKeepsDedicatedRouteWhenAnotherOfferingStillUsesIt() {
        AiChatOffering offering = availableOffering();
        offering.setEnabled(false);
        AiChatOffering other = availableOffering();
        other.setId(12L);
        other.setOfferingCode("story-copy");
        AiRoute route = availableRoute();
        route.setRouteKey("chat.offer.story");

        when(mapper.findOfferingById(11L)).thenReturn(offering);
        when(mapper.findDefaultOffering()).thenReturn(availableOffering());
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);
        when(routingService.isRouteConfigured("chat.offer.story", AiCapability.CHAT)).thenReturn(true);
        when(mapper.listOfferings()).thenReturn(List.of(other));
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);

        service.deleteOffering(11L);

        verify(routingService, never()).deleteRoute(21L);
    }

    @Test
    void firstConcurrentSelectionReturnsConflictInsteadOfInternalError() {
        AiChatOffering offering = availableOffering();
        AiChatOfferingPrice price = new AiChatOfferingPrice();
        price.setOfferingId(11L);
        price.setVipLevel(0);
        price.setBillingMode("FREE");
        AiRoute route = availableRoute();

        when(mapper.findOfferingByCode("story")).thenReturn(offering);
        when(mapper.listPrices(11L)).thenReturn(List.of(price));
        when(routingMapper.findRouteByKey("chat.offer.story")).thenReturn(route);
        when(routingMapper.listRouteMembers(21L)).thenReturn(List.of(new AiRouteMember()));
        when(routingService.isCapabilityEnabled(AiCapability.CHAT)).thenReturn(true);
        when(mapper.insertPreference(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.select(7L, null, "SYSTEM", "story"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他设备创建");
    }

    private static AiChatOffering availableOffering() {
        AiChatOffering offering = new AiChatOffering();
        offering.setId(11L);
        offering.setOfferingCode("story");
        offering.setDisplayName("剧情模型");
        offering.setRouteKey("chat.offer.story");
        offering.setEnabled(true);
        offering.setMaintenance(false);
        offering.setDefaultOffering(true);
        offering.setVipMinLevel(0);
        offering.setVersionNo(0L);
        return offering;
    }

    private static AiRoute availableRoute() {
        AiRoute route = new AiRoute();
        route.setId(21L);
        route.setCapability("CHAT");
        route.setEnabled(true);
        return route;
    }

    private static AiChatOfferingPrice freePrice() {
        AiChatOfferingPrice price = new AiChatOfferingPrice();
        price.setOfferingId(11L);
        price.setVipLevel(0);
        price.setBillingMode("FREE");
        return price;
    }
}
