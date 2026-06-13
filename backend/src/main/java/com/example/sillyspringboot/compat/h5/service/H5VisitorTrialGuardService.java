package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5VisitorDevice;
import com.example.sillyspringboot.compat.h5.mapper.AppH5VisitorDeviceMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationIdempotencyMapper;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.ToIntFunction;

@Service
public class H5VisitorTrialGuardService {

    private final AppFeatureSettingsService featureSettingsService;
    private final H5VisitorDeviceService visitorDeviceService;
    private final AppH5VisitorDeviceMapper visitorDeviceMapper;
    private final AppConversationIdempotencyMapper conversationIdempotencyMapper;
    private final AppTokenService tokenService;
    private final H5ClientUidAuthService h5Auth;

    public H5VisitorTrialGuardService(
            AppFeatureSettingsService featureSettingsService,
            H5VisitorDeviceService visitorDeviceService,
            AppH5VisitorDeviceMapper visitorDeviceMapper,
            AppConversationIdempotencyMapper conversationIdempotencyMapper,
            AppTokenService tokenService,
            H5ClientUidAuthService h5Auth
    ) {
        this.featureSettingsService = featureSettingsService;
        this.visitorDeviceService = visitorDeviceService;
        this.visitorDeviceMapper = visitorDeviceMapper;
        this.conversationIdempotencyMapper = conversationIdempotencyMapper;
        this.tokenService = tokenService;
        this.h5Auth = h5Auth;
    }

    @Transactional
    public void guardAnonymousChatAttempt(String clientUid) {
        if (!isAnonymousRequest(clientUid)) {
            return;
        }
        AppFeatureSettings settings = featureSettingsService.getSettings();
        guardAndIncrement(
                settings.getAnonymousTrialChatLimit(),
                AppH5VisitorDevice::getAnonymousChatAttemptCount,
                device -> visitorDeviceMapper.incrementAnonymousChatAttemptCount(device.getId()),
                "匿名体验聊天次数已达上限，请登录后继续使用"
        );
    }

    @Transactional
    public void guardAnonymousCharacterCreation(String clientUid) {
        if (!isAnonymousRequest(clientUid)) {
            return;
        }
        AppFeatureSettings settings = featureSettingsService.getSettings();
        guardAndIncrement(
                settings.getAnonymousTrialCharacterCreationLimit(),
                AppH5VisitorDevice::getAnonymousCharacterCreateCount,
                device -> visitorDeviceMapper.incrementAnonymousCharacterCreateCount(device.getId()),
                "匿名体验创建角色次数已达上限，请登录后继续使用"
        );
    }

    @Transactional
    public void guardAnonymousConversationCreation(String clientUid, String token, String idempotencyKey) {
        if (!isAnonymousRequest(clientUid)) {
            return;
        }
        long userId = tokenService.validateAndLoadUser(token).getId();
        if (conversationIdempotencyMapper.findByUserAndKey(userId, idempotencyKey) != null) {
            return;
        }
        AppFeatureSettings settings = featureSettingsService.getSettings();
        guardAndIncrement(
                settings.getAnonymousTrialConversationLimit(),
                AppH5VisitorDevice::getAnonymousConversationCreateCount,
                device -> visitorDeviceMapper.incrementAnonymousConversationCreateCount(device.getId()),
                "匿名体验可开启的新会话数量已达上限，请登录后继续使用"
        );
    }

    public static boolean isAnonymousClientUid(String clientUid) {
        return clientUid == null || clientUid.isBlank() || !clientUid.trim().startsWith("h5u_");
    }

    private boolean isAnonymousRequest(String clientUid) {
        if (h5Auth.hasAuthenticatedRequestUser()) {
            return false;
        }
        return isAnonymousClientUid(clientUid);
    }

    private void guardAndIncrement(
            int limit,
            ToIntFunction<AppH5VisitorDevice> counterReader,
            java.util.function.Consumer<AppH5VisitorDevice> incrementAction,
            String limitMessage
    ) {
        if (limit < 0) {
            return;
        }
        if (limit == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, limitMessage);
        }
        AppH5VisitorDevice device = requireCurrentVisitorDevice();
        int used = Math.max(0, counterReader.applyAsInt(device));
        if (used >= limit) {
            throw new BusinessException(ErrorCode.FORBIDDEN, limitMessage);
        }
        incrementAction.accept(device);
    }

    private AppH5VisitorDevice requireCurrentVisitorDevice() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "设备风控初始化失败，请刷新后重试");
        }
        H5VisitorDeviceService.DeviceTouchContext context = visitorDeviceService.resolveOrIssue(request);
        AppH5VisitorDevice device = context == null ? null : visitorDeviceMapper.findByDeviceToken(context.deviceToken());
        if (device == null || device.getId() == null) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "设备风控初始化失败，请刷新后重试");
        }
        return device;
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
