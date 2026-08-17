package com.starsbattle.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway baseline (V1) + main migration chain (V2 roles, V3 roster)
 * produce exactly the schema and seed data required for the app to function —
 * character stats are copied verbatim from the source's characters.seed.cjs.
 */
class FlywayBaselineMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rolesTableContainsAdminAndUserOnly() {
        List<String> roleNames = jdbcTemplate.queryForList(
                "SELECT name FROM roles ORDER BY id ASC", String.class);

        assertThat(roleNames).containsExactly("ADMIN", "USER");
    }

    @Test
    void charactersRosterHasTwelveCharactersOrderedByIdWithSourceStats() {
        List<Map<String, Object>> characters = jdbcTemplate.queryForList(
                "SELECT id, name, hp, base_hp, attack, level_required FROM characters ORDER BY id ASC");

        assertThat(characters).hasSize(12);

        Map<String, Object> first = characters.get(0);
        assertThat(first.get("name")).isEqualTo("Luke Skywalker");
        assertThat(((Number) first.get("hp")).intValue()).isEqualTo(100);
        assertThat(((Number) first.get("base_hp")).intValue()).isEqualTo(100);
        assertThat(((Number) first.get("attack")).intValue()).isEqualTo(20);
        assertThat(((Number) first.get("level_required")).intValue()).isEqualTo(1);

        Map<String, Object> last = characters.get(11);
        assertThat(last.get("name")).isEqualTo("Rey Skywalker");
        assertThat(((Number) last.get("hp")).intValue()).isEqualTo(118);
        assertThat(((Number) last.get("base_hp")).intValue()).isEqualTo(118);
        assertThat(((Number) last.get("attack")).intValue()).isEqualTo(31);
        assertThat(((Number) last.get("level_required")).intValue()).isEqualTo(5);

        Map<String, Object> middle = characters.get(6);
        assertThat(middle.get("name")).isEqualTo("Darth Vader");
        assertThat(((Number) middle.get("hp")).intValue()).isEqualTo(140);
        assertThat(((Number) middle.get("attack")).intValue()).isEqualTo(30);
        assertThat(((Number) middle.get("level_required")).intValue()).isEqualTo(3);
    }

    @Test
    void battlesTableHasNotNullVersionColumnDefaultingToZero() {
        Map<String, Object> columnInfo = jdbcTemplate.queryForMap(
                "SELECT is_nullable, column_default FROM information_schema.columns "
                        + "WHERE table_name = 'battles' AND column_name = 'version'");

        assertThat(columnInfo.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(columnInfo.get("column_default"))).contains("0");
    }
}
