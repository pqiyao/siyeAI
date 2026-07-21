package com.example.sillyspringboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.flyway")
public class LegacyFlywayProperties {

    /**
     * Version used when an older local schema exists but Flyway history does not.
     * Defaulting to 14 allows the idempotent V15 migration to run on legacy databases.
     */
    private String legacyBaselineVersion = "14";

    private String historyTable = "flyway_schema_history";

    public String getLegacyBaselineVersion() {
        return legacyBaselineVersion;
    }

    public void setLegacyBaselineVersion(String legacyBaselineVersion) {
        this.legacyBaselineVersion = legacyBaselineVersion;
    }

    public String getHistoryTable() {
        return historyTable;
    }

    public void setHistoryTable(String historyTable) {
        this.historyTable = historyTable;
    }
}
