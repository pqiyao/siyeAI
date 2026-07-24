package com.example.sillyspringboot.admin.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class AdminDashboardGenerationOpsMapperTest {

    @Autowired
    private AdminDashboardMapper mapper;

    @Test
    void generationOperationsQueriesWorkOnAnEmptyMigratedSchema() {
        LocalDateTime startAt = LocalDateTime.now().minusDays(14);
        assertNotNull(mapper.generationOpsSummary(startAt));
        assertNotNull(mapper.generationLatencyTrend(startAt));
        assertNotNull(mapper.generationProviderStats(startAt));
        assertNotNull(mapper.generationModelStats(startAt));
        assertNotNull(mapper.generationCharacterStats(startAt));
        assertNotNull(mapper.generationErrorStats(startAt));
        assertNotNull(mapper.generationRouteHealth(startAt));
    }
}
