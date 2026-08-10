package com.example.sillyspringboot.character.web;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppCharacterImportControllerSecurityTest {

    @Test
    void legacyAppImportIsAlwaysRejected() {
        AppCharacterImportController controller = new AppCharacterImportController();
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
