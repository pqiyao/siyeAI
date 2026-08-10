package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequest;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequestResponse;
import com.example.sillyspringboot.auth.entity.AppPasswordResetThrottle;
import com.example.sillyspringboot.auth.entity.AppPasswordResetToken;
import com.example.sillyspringboot.auth.mapper.AppPasswordResetTokenMapper;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5PasswordResetServiceTest {

    private AppUserIdentityMapper identityMapper;
    private AppPasswordResetTokenMapper tokenMapper;
    private H5PasswordResetService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setEnabled(true);
        properties.setFrom("noreply@example.com");
        properties.setRequestCooldownSeconds(60);
        properties.setTokenTtlSeconds(600);
        AppAuthProperties authProperties = new AppAuthProperties();
        authProperties.setSecret("unit-test-secret");
        identityMapper = mock(AppUserIdentityMapper.class);
        tokenMapper = mock(AppPasswordResetTokenMapper.class);
        H5PasswordResetConfirmationService confirmationService = mock(H5PasswordResetConfirmationService.class);
        ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        when(mailProvider.getIfAvailable()).thenReturn(mock(JavaMailSender.class));
        Executor directExecutor = Runnable::run;
        service = new H5PasswordResetService(
                properties,
                authProperties,
                identityMapper,
                tokenMapper,
                confirmationService,
                mailProvider,
                directExecutor
        );
    }

    @Test
    void databaseCooldownReturnsExistingRequestWithoutSendingAgain() {
        AppPasswordResetThrottle throttle = new AppPasswordResetThrottle();
        throttle.setAccountKey("hashed-account");
        throttle.setRequestId("existing-request-id");
        throttle.setRequestedAt(LocalDateTime.now().minusSeconds(10));
        when(tokenMapper.insertThrottleIfAbsent(anyString(), anyString())).thenReturn(0);
        when(tokenMapper.findThrottleForUpdate(anyString())).thenReturn(throttle);

        H5PasswordResetRequestResponse response = service.requestReset(request("user@example.com"));

        assertThat(response.requestId()).isEqualTo("existing-request-id");
        assertThat(response.deliveryAvailable()).isTrue();
        assertThat(response.retryAfterSeconds()).isBetween(45L, 60L);
        verify(identityMapper, never()).findByTypeAndKey(anyString(), anyString());
        verify(tokenMapper, never()).insert(any(AppPasswordResetToken.class));
    }

    @Test
    void unknownEmailGetsIndistinguishablePersistedPlaceholderWithoutPlaintext() {
        AppPasswordResetThrottle throttle = new AppPasswordResetThrottle();
        throttle.setAccountKey("placeholder-hash");
        throttle.setRequestId("new-request-id");
        throttle.setRequestedAt(LocalDateTime.now());
        when(tokenMapper.insertThrottleIfAbsent(anyString(), anyString())).thenReturn(1);
        when(tokenMapper.findThrottleForUpdate(anyString())).thenReturn(throttle);
        when(identityMapper.findByTypeAndKey("h5_account", "missing@example.com")).thenReturn(null);

        H5PasswordResetRequestResponse response = service.requestReset(request("missing@example.com"));

        ArgumentCaptor<AppPasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(AppPasswordResetToken.class);
        verify(tokenMapper).insert(tokenCaptor.capture());
        AppPasswordResetToken token = tokenCaptor.getValue();
        assertThat(response.deliveryAvailable()).isTrue();
        assertThat(response.requestId()).isEqualTo(token.getRequestId());
        assertThat(token.getUserId()).isNull();
        assertThat(token.getAccountKey()).hasSize(64).doesNotContain("missing@example.com");
    }

    private static H5PasswordResetRequest request(String email) {
        H5PasswordResetRequest request = new H5PasswordResetRequest();
        request.setEmail(email);
        return request;
    }
}
