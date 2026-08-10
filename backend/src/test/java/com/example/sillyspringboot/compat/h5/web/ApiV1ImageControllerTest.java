package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ops.service.ImageGenerationFacade;
import com.example.sillyspringboot.ops.service.MockImageGenerationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1ImageControllerTest {

    @Test
    void resultEndpointExposesDoneResultForClientRecovery() {
        MockImageGenerationService mockService = mock(MockImageGenerationService.class);
        ImageGenerationFacade facade = mock(ImageGenerationFacade.class);
        ApiV1ImageController controller = new ApiV1ImageController(mockService, facade);
        Map<String, Object> result = Map.of(
                "status", "DONE",
                "imageRequestId", "image_request_1",
                "images", java.util.List.of(Map.of("url", "data:image/png;base64,abc"))
        );
        when(facade.findResult("client", "image_request_1")).thenReturn(result);

        ApiV1Result<Map<String, Object>> response = controller.result("client", "image_request_1");

        assertThat(response.code()).isEqualTo(1);
        assertThat(response.data()).containsEntry("status", "DONE");
        verify(facade).findResult("client", "image_request_1");
    }
}
