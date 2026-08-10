package com.example.sillyspringboot.ops.retention;

import com.example.sillyspringboot.config.GenerationRetentionProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationRetentionServiceTest {

    @Test
    void archivesExpiredDetailsInBoundedBatches() {
        GenerationRetentionProperties properties = new GenerationRetentionProperties();
        properties.setRetentionDays(30);
        properties.setBatchSize(2000);
        properties.setMaxBatchesPerRun(3);
        GenerationRetentionWriter writer = mock(GenerationRetentionWriter.class);
        when(writer.archiveTaskBatch(any(LocalDateTime.class), eq(2000))).thenReturn(2000, 8);
        when(writer.archiveOrphanAttemptBatch(any(LocalDateTime.class), eq(2000))).thenReturn(4);
        when(writer.archiveOrphanEventBatch(any(LocalDateTime.class), eq(2000))).thenReturn(3);

        int archived = new GenerationRetentionService(properties, writer).cleanupNow();

        assertThat(archived).isEqualTo(2015);
        verify(writer, times(2)).archiveTaskBatch(any(LocalDateTime.class), eq(2000));
        verify(writer).archiveOrphanAttemptBatch(any(LocalDateTime.class), eq(2000));
        verify(writer).archiveOrphanEventBatch(any(LocalDateTime.class), eq(2000));
    }

    @Test
    void cleanupCanBeDisabled() {
        GenerationRetentionProperties properties = new GenerationRetentionProperties();
        properties.setEnabled(false);
        GenerationRetentionWriter writer = mock(GenerationRetentionWriter.class);

        int archived = new GenerationRetentionService(properties, writer).cleanupNow();

        assertThat(archived).isZero();
        verify(writer, never()).archiveTaskBatch(any(), eq(2000));
    }
}
