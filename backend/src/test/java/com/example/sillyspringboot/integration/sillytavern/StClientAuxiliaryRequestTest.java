package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StClientAuxiliaryRequestTest {

    @Test
    void onlySemanticAnnotationModeIsIsolatedFromProviderHealth() {
        assertTrue(StClient.isAuxiliarySidecarRequest(request("semantic_annotation")));
        assertTrue(StClient.isAuxiliarySidecarRequest(request("SEMANTIC_ANNOTATION")));
        assertFalse(StClient.isAuxiliarySidecarRequest(request("generate")));
        assertFalse(StClient.isAuxiliarySidecarRequest(null));
    }

    private static ChatGenerateRequest request(String mode) {
        return new ChatGenerateRequest(
                1L, "", List.of(), "test", true, mode, Set.of(), "", "", List.of(),
                "", "", "", List.of(), null
        );
    }
}
