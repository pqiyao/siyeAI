package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class H5VisitorTrialGuardService {

    private final H5ClientUidAuthService h5Auth;

    public H5VisitorTrialGuardService(H5ClientUidAuthService h5Auth) {
        this.h5Auth = h5Auth;
    }

    public void guardAnonymousChatAttempt(String clientUid) {
        if (!isAnonymousRequest()) {
            return;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后继续聊天");
    }

    public void guardAnonymousCharacterCreation(String clientUid) {
        if (!isAnonymousRequest()) {
            return;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后再创建角色");
    }

    public void guardAnonymousConversationCreation(String clientUid, String token, String idempotencyKey) {
        if (!isAnonymousRequest()) {
            return;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后继续聊天");
    }

    private boolean isAnonymousRequest() {
        return !h5Auth.hasAuthenticatedRequestUser();
    }
}
