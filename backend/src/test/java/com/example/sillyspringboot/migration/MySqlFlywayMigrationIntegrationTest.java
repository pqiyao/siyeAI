package com.example.sillyspringboot.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MYSQL_MIGRATION_TEST_URL", matches = ".+")
class MySqlFlywayMigrationIntegrationTest {

    @Test
    void migratesAnEmptyMySqlSchemaThroughTheExpectedVersion() throws Exception {
        String url = requiredEnvironment("MYSQL_MIGRATION_TEST_URL");
        String username = requiredEnvironment("MYSQL_MIGRATION_TEST_USERNAME");
        String password = requiredEnvironment("MYSQL_MIGRATION_TEST_PASSWORD");
        String expectedVersion = requiredEnvironment("MYSQL_MIGRATION_TEST_EXPECTED_VERSION");

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).as("migration test schema must start empty").isZero();
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration-common", "classpath:db/migration-mysql")
                .validateMigrationNaming(true)
                .load();

        flyway.migrate();
        flyway.validate();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet failed = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0")) {
                assertThat(failed.next()).isTrue();
                assertThat(failed.getInt(1)).as("failed Flyway migrations").isZero();
            }
            try (ResultSet latest = statement.executeQuery(
                    "SELECT version FROM flyway_schema_history "
                            + "WHERE success = 1 AND version IS NOT NULL "
                            + "ORDER BY installed_rank DESC LIMIT 1")) {
                assertThat(latest.next()).isTrue();
                assertThat(latest.getString(1)).isEqualTo(expectedVersion);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name).isNotBlank();
        return value.trim();
    }
}
