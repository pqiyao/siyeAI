package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImageGenerationAssetStorageServiceTest {

    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @TempDir
    Path tempDir;

    @Test
    void replacesDataUrlWithServerAssetUrlBeforeResultCaching() throws Exception {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        ImageGenerationAssetStorageService service =
                new ImageGenerationAssetStorageService(tempDir.toString(), properties);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_1X1);

        Map<String, Object> result = service.externalize(42L, "image_request_1", Map.of(
                "images", List.of(Map.of("url", dataUrl, "width", 1, "height", 1))
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> image = ((List<Map<String, Object>>) result.get("images")).get(0);
        String url = String.valueOf(image.get("url"));
        assertThat(url).startsWith("/uploads/generated/").endsWith(".png");
        assertThat(url).doesNotContain("base64");
        assertThat(image).containsEntry("storage", "server").containsEntry("byteSize", PNG_1X1.length);

        String relative = url.substring("/uploads/generated/".length());
        Path stored = tempDir.resolve("generated").resolve(relative);
        assertThat(stored).isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(PNG_1X1);
    }

    @Test
    void leavesRemoteImageUrlsUntouched() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        ImageGenerationAssetStorageService service =
                new ImageGenerationAssetStorageService(tempDir.toString(), properties);

        Map<String, Object> result = service.externalize(42L, "image_request_2", Map.of(
                "images", List.of(Map.of("url", "https://assets.example/image.png"))
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> image = ((List<Map<String, Object>>) result.get("images")).get(0);
        assertThat(image).containsEntry("url", "https://assets.example/image.png");
    }
}
