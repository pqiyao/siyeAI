-- Phase 1: portable placeholder so Flyway validates against MySQL (dev) and H2 (test).
CREATE TABLE IF NOT EXISTS phase1_schema_ok (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
);
