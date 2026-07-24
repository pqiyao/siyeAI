package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class H5SecurityEventRetentionMapperIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppH5SecurityEventMapper mapper;

    @Test
    void deletesOnlyEventsOlderThanTheCutoff() {
        String suffix = Long.toString(Math.abs(System.nanoTime()));
        String oldDetail = "retention-old-" + suffix;
        String recentDetail = "retention-recent-" + suffix;
        jdbcTemplate.update(
                "INSERT INTO app_h5_security_event (event_type, detail, created_at) VALUES (?, ?, ?)",
                "RETENTION_TEST",
                oldDetail,
                LocalDateTime.now().minusYears(100)
        );
        jdbcTemplate.update(
                "INSERT INTO app_h5_security_event (event_type, detail, created_at) VALUES (?, ?, ?)",
                "RETENTION_TEST",
                recentDetail,
                LocalDateTime.now()
        );

        int deleted = mapper.deleteOldestBefore(LocalDateTime.now().minusDays(90), 100);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(countByDetail(oldDetail)).isZero();
        assertThat(countByDetail(recentDetail)).isEqualTo(1);
    }

    private int countByDetail(String detail) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_h5_security_event WHERE detail = ?",
                Integer.class,
                detail
        );
        return count == null ? 0 : count;
    }
}
