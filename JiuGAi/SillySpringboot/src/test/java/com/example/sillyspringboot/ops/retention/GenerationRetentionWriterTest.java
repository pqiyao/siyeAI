package com.example.sillyspringboot.ops.retention;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationRetentionWriterTest {

    @Test
    void archivesBeforeDeletingTaskDetails() {
        GenerationRetentionMapper mapper = mock(GenerationRetentionMapper.class);
        GenerationRetentionWriter writer = new GenerationRetentionWriter(mapper);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        when(mapper.deleteTaskBatch(cutoff, 2000)).thenReturn(12);

        int deleted = writer.archiveTaskBatch(cutoff, 2000);

        assertThat(deleted).isEqualTo(12);
        var order = inOrder(mapper);
        order.verify(mapper).aggregateTaskBatch(cutoff, 2000);
        order.verify(mapper).aggregateAttemptsForTaskBatch(cutoff, 2000);
        order.verify(mapper).deleteAttemptsForTaskBatch(cutoff, 2000);
        order.verify(mapper).deleteStatEventsForTaskBatch(cutoff, 2000);
        order.verify(mapper).deleteTaskBatch(cutoff, 2000);
    }
}
