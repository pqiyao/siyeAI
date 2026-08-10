package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StWorldbookCatalogServiceTest {

    @Test
    void listAvailableWorldbooks_shouldReuseShortLivedCacheAndAllowInvalidation() {
        StAdapter stAdapter = mock(StAdapter.class);
        when(stAdapter.listWorldbooks()).thenReturn(List.of(
                new StWorldbookOptionDto("world_a", "World A"),
                new StWorldbookOptionDto("world_b", "World B")
        ));
        StWorldbookCatalogService service = new StWorldbookCatalogService(stAdapter);

        assertThat(service.listAvailableWorldbooks()).extracting(StWorldbookOptionDto::fileId)
                .containsExactly("world_a", "world_b");
        assertThat(service.normalizeAndFilterAvailableWorldNames(List.of("world_b", "missing")))
                .containsExactly("world_b");
        verify(stAdapter, times(1)).listWorldbooks();

        service.invalidateCache();
        assertThat(service.listAvailableWorldbooks()).extracting(StWorldbookOptionDto::fileId)
                .containsExactly("world_a", "world_b");
        verify(stAdapter, times(2)).listWorldbooks();
    }
}
