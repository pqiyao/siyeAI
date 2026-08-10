package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiV1ExceptionHandlerTest {

    @Test
    void returnsHttp409ForPreferenceConflict() {
        ApiV1ExceptionHandler handler = new ApiV1ExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/chat/preferences");

        Object response = handler.handleBusiness(
                new BusinessException(ErrorCode.CONFLICT, "配置已在其他设备更新"),
                request
        );

        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> entity = (ResponseEntity<?>) response;
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(entity.getBody()).isInstanceOf(ApiV1Result.class);
        ApiV1Result<?> body = (ApiV1Result<?>) entity.getBody();
        assertThat(body.code()).isZero();
        assertThat(body.msg()).isEqualTo("配置已在其他设备更新");
    }
}
