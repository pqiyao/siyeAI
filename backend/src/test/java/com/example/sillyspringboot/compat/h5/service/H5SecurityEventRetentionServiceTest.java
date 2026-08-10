package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.config.H5SecurityEventProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class H5SecurityEventRetentionServiceTest {

    @Test
    void deletesExpiredEventsInBoundedBatches() {
        AppH5SecurityEventMapper mapper = mock(AppH5SecurityEventMapper.class);
        H5SecurityEventProperties properties = new H5SecurityEventProperties();
        properties.setRetentionDays(30);
        properties.setCleanupBatchSize(2000);
        properties.setMaxBatchesPerRun(3);
        when(mapper.deleteOldestBefore(any(LocalDateTime.class), eq(2000)))
                .thenReturn(2000, 17);

        int deleted = new H5SecurityEventRetentionService(mapper, properties).cleanupNow();

        assertThat(deleted).isEqualTo(2017);
        verify(mapper, times(2)).deleteOldestBefore(any(LocalDateTime.class), eq(2000));
    }

    @Test
    void cleanupCanBeDisabled() {
        AppH5SecurityEventMapper mapper = mock(AppH5SecurityEventMapper.class);
        H5SecurityEventProperties properties = new H5SecurityEventProperties();
        properties.setCleanupEnabled(false);

        int deleted = new H5SecurityEventRetentionService(mapper, properties).cleanupNow();

        assertThat(deleted).isZero();
        verifyNoInteractions(mapper);
    }
}
