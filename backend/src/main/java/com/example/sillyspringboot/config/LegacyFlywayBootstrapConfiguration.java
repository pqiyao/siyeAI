package com.example.sillyspringboot.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

@Configuration
public class LegacyFlywayBootstrapConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LegacyFlywayBootstrapConfiguration.class);
    private static final Set<String> KNOWN_LATE_MIGRATIONS = Set.of("99");
    private static final Set<String> REPAIRABLE_FAILED_MIGRATIONS = Set.of("23", "24", "27", "34", "35", "45", "99");
    private static final Set<String> REPAIRABLE_CHECKSUM_MIGRATIONS = Set.of("69", "99");

    @Bean
    public InitializingBean legacyFlywayBootstrapRunner(DataSource dataSource, LegacyFlywayProperties properties) {
        return () -> {
            String baselineVersion = detectBaselineVersion(dataSource, properties);
            String historyTable = resolveHistoryTable(properties);

            FluentConfiguration configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(properties.getLocations().toArray(String[]::new))
                    .baselineOnMigrate(true)
                    .cleanDisabled(true);

            if (StringUtils.hasText(baselineVersion)) {
                configuration.baselineVersion(MigrationVersion.fromVersion(baselineVersion));
                configuration.baselineDescription("legacy schema bootstrap");
                log.warn("Flyway history table missing on a populated schema, applying legacy baseline {}", baselineVersion);
            }

            Flyway flyway = configuration.load();
            if (hasRepairableFailedMigration(dataSource, historyTable)
                    && hasOnlyRepairableValidationErrors(flyway)) {
                log.warn("Detected repairable failed Flyway history entries, repairing schema history before retry");
                flyway.repair();
            }
            if (!hasFailedMigration(dataSource, historyTable)
                    && hasRepairableChecksumMismatch(flyway)
                    && hasOnlyRepairableValidationErrors(flyway)) {
                log.warn("Detected repairable Flyway checksum mismatch, repairing schema history before migrate");
                flyway.repair();
            }
            if (hasOnlyKnownLateMigrations(flyway)) {
                log.warn("Detected known late Flyway migration V99; enabling out-of-order for this migration run only");
                flyway = configuration.outOfOrder(true).load();
            }
            MigrateResult result = flyway.migrate();
            log.info(
                    "Flyway migrate finished: initialVersion={}, targetVersion={}, migrationsExecuted={}",
                    result.initialSchemaVersion,
                    result.targetSchemaVersion,
                    result.migrationsExecuted
            );
        };
    }

    private String detectBaselineVersion(DataSource dataSource, LegacyFlywayProperties properties) throws SQLException {
        String historyTable = resolveHistoryTable(properties);
        if (tableExists(dataSource, historyTable)) {
            return null;
        }
        if (!looksLikeLegacySchema(dataSource)) {
            return null;
        }
        if (hasV15Sentinels(dataSource)) {
            return "15";
        }
        return StringUtils.hasText(properties.getLegacyBaselineVersion())
                ? properties.getLegacyBaselineVersion().trim()
                : "14";
    }

    private String resolveHistoryTable(LegacyFlywayProperties properties) {
        return StringUtils.hasText(properties.getHistoryTable())
                ? properties.getHistoryTable().trim()
                : "flyway_schema_history";
    }

    private boolean looksLikeLegacySchema(DataSource dataSource) throws SQLException {
        return tableExists(dataSource, "app_user")
                || tableExists(dataSource, "app_character")
                || tableExists(dataSource, "app_generation_task")
                || tableExists(dataSource, "app_conversation");
    }

    private boolean hasV15Sentinels(DataSource dataSource) throws SQLException {
        return tableExists(dataSource, "app_openrouter_generation_settings")
                || tableExists(dataSource, "app_lorebook_entry")
                || tableExists(dataSource, "app_admin_notice_read")
                || columnExists(dataSource, "app_notice", "sort_order")
                || columnExists(dataSource, "app_generation_task", "channel");
    }

    private boolean tableExists(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = connection.getSchema();
            if (tableExists(metaData, connection.getCatalog(), schema, tableName)) {
                return true;
            }
            if (tableExists(metaData, connection.getCatalog(), schema, tableName.toUpperCase())) {
                return true;
            }
            return tableExists(metaData, connection.getCatalog(), schema, tableName.toLowerCase());
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean columnExists(DataSource dataSource, String tableName, String columnName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = connection.getSchema();
            if (columnExists(metaData, connection.getCatalog(), schema, tableName, columnName)) {
                return true;
            }
            if (columnExists(metaData, connection.getCatalog(), schema, tableName.toUpperCase(), columnName.toUpperCase())) {
                return true;
            }
            return columnExists(metaData, connection.getCatalog(), schema, tableName.toLowerCase(), columnName.toLowerCase());
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String catalog, String schema, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, columnName)) {
            return rs.next();
        }
    }

    private boolean hasRepairableFailedMigration(DataSource dataSource, String historyTable) throws SQLException {
        if (!tableExists(dataSource, historyTable)) {
            return false;
        }
        String sql = "SELECT version FROM `" + historyTable + "` WHERE success = 0";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            boolean found = false;
            while (rs.next()) {
                String version = rs.getString(1);
                if (!REPAIRABLE_FAILED_MIGRATIONS.contains(version)) {
                    return false;
                }
                found = true;
            }
            return found;
        }
    }

    private boolean hasFailedMigration(DataSource dataSource, String historyTable) throws SQLException {
        if (!tableExists(dataSource, historyTable)) {
            return false;
        }
        String sql = "SELECT 1 FROM `" + historyTable + "` WHERE success = 0 LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasRepairableChecksumMismatch(Flyway flyway) {
        ValidateResult result = flyway.validateWithResult();
        if (result.validationSuccessful || result.invalidMigrations == null) {
            return false;
        }
        boolean repairableMismatchFound = false;
        for (ValidateOutput invalid : result.invalidMigrations) {
            String message = invalid != null && invalid.errorDetails != null
                    ? invalid.errorDetails.errorMessage
                    : "";
            if (message == null || !message.toLowerCase().contains("checksum mismatch")) {
                continue;
            }
            String version = invalid != null ? invalid.version : null;
            if (!REPAIRABLE_CHECKSUM_MIGRATIONS.contains(version)) {
                return false;
            }
            repairableMismatchFound = true;
        }
        return repairableMismatchFound;
    }

    private boolean hasOnlyRepairableValidationErrors(Flyway flyway) {
        ValidateResult result = flyway.validateWithResult();
        if (result.validationSuccessful || result.invalidMigrations == null || result.invalidMigrations.isEmpty()) {
            return true;
        }
        for (ValidateOutput invalid : result.invalidMigrations) {
            String version = invalid == null ? null : invalid.version;
            String errorCode = invalid != null && invalid.errorDetails != null
                    ? String.valueOf(invalid.errorDetails.errorCode)
                    : "";
            if ("FAILED_VERSIONED_MIGRATION".equals(errorCode)
                    && REPAIRABLE_FAILED_MIGRATIONS.contains(version)) {
                continue;
            }
            if ("CHECKSUM_MISMATCH".equals(errorCode)
                    && REPAIRABLE_CHECKSUM_MIGRATIONS.contains(version)) {
                continue;
            }
            if ("RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(errorCode)
                    && KNOWN_LATE_MIGRATIONS.contains(version)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean hasOnlyKnownLateMigrations(Flyway flyway) {
        ValidateResult result = flyway.validateWithResult();
        if (result.validationSuccessful || result.invalidMigrations == null || result.invalidMigrations.isEmpty()) {
            return false;
        }
        var current = flyway.info().current();
        if (current == null || current.getVersion() == null) {
            return false;
        }
        MigrationVersion currentVersion = current.getVersion();
        boolean knownLateMigrationFound = false;
        for (ValidateOutput invalid : result.invalidMigrations) {
            String version = invalid == null ? null : invalid.version;
            String errorCode = invalid != null && invalid.errorDetails != null
                    ? String.valueOf(invalid.errorDetails.errorCode)
                    : "";
            if (!"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(errorCode) || !StringUtils.hasText(version)) {
                return false;
            }
            MigrationVersion invalidVersion = MigrationVersion.fromVersion(version);
            if (invalidVersion.compareTo(currentVersion) < 0) {
                if (!KNOWN_LATE_MIGRATIONS.contains(version)) {
                    return false;
                }
                knownLateMigrationFound = true;
            }
        }
        return knownLateMigrationFound;
    }
}
