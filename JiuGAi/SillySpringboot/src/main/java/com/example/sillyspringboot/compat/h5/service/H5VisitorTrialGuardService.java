package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class H5VisitorTrialGuardService {

    private static final Logger log = LoggerFactory.getLogger(H5VisitorTrialGuardService.class);

    private final H5ClientUidAuthService h5Auth;
    private final H5VisitorDeviceService visitorDeviceService;

    public H5VisitorTrialGuardService(
            H5ClientUidAuthService h5Auth,
            H5VisitorDeviceService visitorDeviceService
    ) {
        this.h5Auth = h5Auth;
        this.visitorDeviceService = visitorDeviceService;
    }

    public void guardAnonymousChatAttempt(String clientUid) {
        if (!isAnonymousRequest()) {
            return;
        }
        recordAttempt(H5VisitorDeviceService.AnonymousAction.CHAT, clientUid);
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后继续聊天");
    }

    public void guardAnonymousCharacterCreation(String clientUid) {
        if (!isAnonymousRequest()) {
            return;
        }
        recordAttempt(H5VisitorDeviceService.AnonymousAction.CHARACTER, clientUid);
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再创建角色");
    }

    public void guardAnonymousConversationCreation(String clientUid, String token, String idempotencyKey) {
        if (!isAnonymousRequest()) {
            return;
        }
        recordAttempt(H5VisitorDeviceService.AnonymousAction.CONVERSATION, clientUid);
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后继续聊天");
    }

    private boolean isAnonymousRequest() {
        return !h5Auth.hasAuthenticatedRequestUser();
    }

    private void recordAttempt(H5VisitorDeviceService.AnonymousAction action, String clientUid) {
        try {
            visitorDeviceService.recordAnonymousAttempt(currentRequest(), action, clientUid);
        } catch (RuntimeException ex) {
            log.warn("failed to record anonymous visitor attempt action={}: {}", action, ex.getMessage());
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
