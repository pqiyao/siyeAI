package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.auth.dto.H5PasswordResetConfirmRequest;
import com.example.sillyspringboot.auth.entity.AppPasswordResetToken;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppPasswordResetTokenMapper;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5PasswordResetConfirmationServiceTest {

    private static final String SECRET = "unit-test-secret";
    private static final String REQUEST_ID = "b5c1d8ae-b3e4-4be4-9728-5d345489c06b";
    private static final String EMAIL = "user@example.com";
    private static final String CODE = "12345678";

    private AppUserIdentityMapper identityMapper;
    private AppPasswordResetTokenMapper tokenMapper;
    private AppUserSessionMapper sessionMapper;
    private PasswordEncoder passwordEncoder;
    private H5PasswordResetConfirmationService service;

    @BeforeEach
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setMaxAttempts(5);
        AppAuthProperties authProperties = new AppAuthProperties();
        authProperties.setSecret(SECRET);
        identityMapper = mock(AppUserIdentityMapper.class);
        tokenMapper = mock(AppPasswordResetTokenMapper.class);
        sessionMapper = mock(AppUserSessionMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new H5PasswordResetConfirmationService(
                properties,
                authProperties,
                identityMapper,
                tokenMapper,
                sessionMapper,
                passwordEncoder
        );
    }

    @Test
    void wrongCodePersistsAttemptWithoutChangingPassword() {
        AppPasswordResetToken token = validToken();
        when(tokenMapper.findByRequestIdForUpdate(REQUEST_ID)).thenReturn(token);

        H5PasswordResetConfirmRequest request = request("87654321");

        assertThat(service.confirm(request)).isFalse();
        verify(tokenMapper).incrementAttemptCount(token.getId(), 5);
        verify(identityMapper, never()).updateById(org.mockito.ArgumentMatchers.any());
        verify(sessionMapper, never()).revokeActiveByUserId(token.getUserId());
    }

    @Test
    void correctCodeConsumesTokenChangesPasswordAndRevokesSessions() {
        AppPasswordResetToken token = validToken();
        AppUserIdentity identity = new AppUserIdentity();
        identity.setId(21L);
        identity.setUserId(token.getUserId());
        identity.setIdentityType("h5_account");
        identity.setIdentityKey(EMAIL);
        identity.setVerified(Boolean.TRUE);
        identity.setCredentialHash("old-hash");
        when(tokenMapper.findByRequestIdForUpdate(REQUEST_ID)).thenReturn(token);
        when(identityMapper.findByTypeAndKey("h5_account", EMAIL)).thenReturn(identity);
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        assertThat(service.confirm(request(CODE))).isTrue();
        assertThat(identity.getCredentialHash()).isEqualTo("new-hash");
        verify(identityMapper).updateById(identity);
        verify(tokenMapper).markConsumed(token.getId());
        verify(tokenMapper).invalidateActiveByUserId(token.getUserId());
        verify(sessionMapper).revokeActiveByUserId(token.getUserId());
    }

    @Test
    void expiredCodeDoesNotTouchCredentials() {
        AppPasswordResetToken token = validToken();
        token.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(tokenMapper.findByRequestIdForUpdate(REQUEST_ID)).thenReturn(token);

        assertThat(service.confirm(request(CODE))).isFalse();
        verify(identityMapper, never()).findByTypeAndKey(anyString(), anyString());
        verify(tokenMapper, never()).incrementAttemptCount(token.getId(), 5);
    }

    private AppPasswordResetToken validToken() {
        AppPasswordResetToken token = new AppPasswordResetToken();
        token.setId(11L);
        token.setRequestId(REQUEST_ID);
        token.setUserId(7L);
        token.setAccountKey(hashAccount(EMAIL));
        token.setCodeHash(hash(REQUEST_ID, CODE));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setAttemptCount(0);
        return token;
    }

    private static H5PasswordResetConfirmRequest request(String code) {
        H5PasswordResetConfirmRequest request = new H5PasswordResetConfirmRequest();
        request.setRequestId(REQUEST_ID);
        request.setEmail(EMAIL);
        request.setCode(code);
        request.setNewPassword("new-password");
        return request;
    }

    private static String hash(String requestId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((SECRET + ':' + requestId + ':' + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(value & 0x0f, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String hashAccount(String account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((SECRET + ":password-reset-account:" + account).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(value & 0x0f, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
