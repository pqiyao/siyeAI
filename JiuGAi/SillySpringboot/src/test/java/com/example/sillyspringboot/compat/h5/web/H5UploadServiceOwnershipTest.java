package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.compat.h5.service.H5UploadAssetOwnershipService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class H5UploadServiceOwnershipTest {

    @TempDir
    Path tempDir;

    @Test
    void authenticatedUploadPersistsServerResolvedOwner() {
        H5UploadAssetOwnershipService ownershipService = mock(H5UploadAssetOwnershipService.class);
        H5UploadService service = new H5UploadService(tempDir.toString(), ownershipService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "png".getBytes());

        String url = service.saveOwnedImageAndGetUrl(file, 42L);
        String relativePath = url.substring("/uploads/h5/".length());

        verify(ownershipService).registerOwnedAsset(42L, url, relativePath);
        assertThat(tempDir.resolve("h5").resolve(relativePath)).exists();
    }

    @Test
    void explicitlyUnownedUploadDoesNotCreateUserOwnership() {
        H5UploadAssetOwnershipService ownershipService = mock(H5UploadAssetOwnershipService.class);
        H5UploadService service = new H5UploadService(tempDir.toString(), ownershipService);
        MockMultipartFile file = new MockMultipartFile("file", "public.png", "image/png", "png".getBytes());

        String url = service.saveUnownedImageAndGetUrl(file);

        assertThat(url).startsWith("/uploads/h5/");
        verifyNoInteractions(ownershipService);
    }

    @Test
    void ownershipPersistenceFailureRemovesNewFile() throws Exception {
        H5UploadAssetOwnershipService ownershipService = mock(H5UploadAssetOwnershipService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(ownershipService).registerOwnedAsset(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        H5UploadService service = new H5UploadService(tempDir.toString(), ownershipService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "png".getBytes());

        assertThatThrownBy(() -> service.saveOwnedImageAndGetUrl(file, 42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传失败");

        Path folder = tempDir.resolve("h5");
        assertThat(Files.exists(folder) ? Files.list(folder).toList() : java.util.List.of()).isEmpty();
    }

    @Test
    void systemImageCopyRemainsAfterOwnedSourceIsHardDeleted() throws Exception {
        H5UploadAssetOwnershipService ownershipService = mock(H5UploadAssetOwnershipService.class);
        H5UploadService service = new H5UploadService(tempDir.toString(), ownershipService);
        MockMultipartFile sourceFile = new MockMultipartFile(
                "file", "avatar.png", "image/png", "independent-image".getBytes()
        );
        String sourceUrl = service.saveOwnedImageAndGetUrl(sourceFile, 42L);

        String systemUrl = service.copyUnownedImageAndGetUrl(sourceUrl);
        Path sourcePath = tempDir.resolve("h5").resolve(sourceUrl.substring("/uploads/h5/".length()));
        Path systemPath = tempDir.resolve("h5").resolve(systemUrl.substring("/uploads/h5/".length()));
        Files.delete(sourcePath);

        assertThat(systemUrl).isNotEqualTo(sourceUrl);
        assertThat(systemPath).exists();
        assertThat(Files.readString(systemPath)).isEqualTo("independent-image");
        verify(ownershipService).registerOwnedAsset(
                42L,
                sourceUrl,
                sourceUrl.substring("/uploads/h5/".length())
        );
    }
}
