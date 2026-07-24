package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.character.service.CharacterCatalogService;
import com.example.sillyspringboot.character.service.EmbeddedLorebookSyncService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ApiV1CharacterImportPngControllerSecurityTest {

    @Test
    void importRejectsSpoofedClientUidBeforeCallingSillyTavern() {
        StAdapter stAdapter = mock(StAdapter.class);
        CharacterCatalogService catalog = mock(CharacterCatalogService.class);
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        H5VisitorTrialGuardService trialGuard = mock(H5VisitorTrialGuardService.class);
        EmbeddedLorebookSyncService lorebookSync = mock(EmbeddedLorebookSyncService.class);
        ApiV1CharacterImportPngController controller = new ApiV1CharacterImportPngController(
                stAdapter,
                catalog,
                h5Auth,
                trialGuard,
                lorebookSync
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("clientUid", "h5u_42");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "card.png",
                "image/png",
                "png".getBytes()
        );
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"))
                .when(h5Auth).requireAuthenticatedTokenForClientUid("h5u_42");

        assertThatThrownBy(() -> controller.importPng(file, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(stAdapter, catalog, lorebookSync);
    }
}
