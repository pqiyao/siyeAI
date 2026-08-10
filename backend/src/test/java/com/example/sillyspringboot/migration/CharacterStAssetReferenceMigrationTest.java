package com.example.sillyspringboot.migration;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterStAssetReferenceMigrationTest {

    @Test
    void migrationReplacesExpiringProxyUrlsWithEachRowsOwnStFile() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:character_st_asset_v108_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        );
        dataSource.setUser("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-common", "classpath:db/migration-h2")
                .target("107")
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)", 990001L, "migration-user");
        long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = 990001",
                Long.class
        );
        jdbc.update(
                """
                INSERT INTO app_character
                    (st_avatar_url, name, owner_user_id, private_card, avatar_url, cover_url)
                VALUES (?, 'private', ?, TRUE, ?, ?)
                """,
                "private-own.png",
                userId,
                "/api/v1/st-assets/characters/private-own.png?expires=1&sig=old",
                "/api/v1/st-assets/characters-thumb/private-own.png?expires=1&sig=old&preset=card"
        );
        jdbc.update(
                """
                INSERT INTO app_character
                    (st_avatar_url, name, owner_user_id, private_card, avatar_url, cover_url)
                VALUES (?, 'system', NULL, FALSE, ?, ?)
                """,
                "system-own.png",
                "/api/v1/st-assets/characters/private-own.png?expires=1&sig=old",
                "/api/v1/st-assets/characters-thumb/private-own.png?expires=1&sig=old&preset=card"
        );

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-common", "classpath:db/migration-h2")
                .load();
        flyway.migrate();
        flyway.validate();

        assertThat(jdbc.queryForMap(
                "SELECT avatar_url, cover_url FROM app_character WHERE st_avatar_url = 'private-own.png'"
        )).containsEntry("avatar_url", "private-own.png")
                .containsEntry("cover_url", "private-own.png");
        assertThat(jdbc.queryForMap(
                "SELECT avatar_url, cover_url FROM app_character WHERE st_avatar_url = 'system-own.png'"
        )).containsEntry("avatar_url", "system-own.png")
                .containsEntry("cover_url", "system-own.png");
    }
}
