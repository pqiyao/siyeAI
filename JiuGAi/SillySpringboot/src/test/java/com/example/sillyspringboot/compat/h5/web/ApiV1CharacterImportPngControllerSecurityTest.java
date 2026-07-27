package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiV1CharacterImportPngControllerSecurityTest {

    @Test
    void legacyUnscopedImportIsAlwaysRejected() {
        ApiV1CharacterImportPngController controller = new ApiV1CharacterImportPngController();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "card.png",
                "image/png",
                "png".getBytes()
        );

        assertThatThrownBy(() -> controller.importPng(file))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_OPERATION);
    }
}
