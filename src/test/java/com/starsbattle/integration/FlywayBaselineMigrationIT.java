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

        Object[][] expected = {
                {"Luke Skywalker", 100, 100, 20, 1},
                {"Han Solo", 90, 90, 18, 1},
                {"Leia Organa", 95, 95, 19, 1},
                {"Obi-Wan Kenobi", 120, 120, 24, 2},
                {"Boba Fett", 105, 105, 22, 2},
                {"Ahsoka Tano", 115, 115, 25, 2},
                {"Darth Vader", 140, 140, 30, 3},
                {"Mace Windu", 130, 130, 28, 3},
                {"Yoda", 110, 110, 32, 4},
                {"Darth Maul", 125, 125, 29, 4},
                {"Emperor Palpatine", 100, 100, 35, 5},
                {"Rey Skywalker", 118, 118, 31, 5},
        };

        for (int i = 0; i < expected.length; i++) {
            Map<String, Object> row = characters.get(i);
            assertThat(row.get("name")).as("name at index %d", i).isEqualTo(expected[i][0]);
            assertThat(((Number) row.get("hp")).intValue()).as("hp at index %d", i).isEqualTo(expected[i][1]);
            assertThat(((Number) row.get("base_hp")).intValue()).as("base_hp at index %d", i).isEqualTo(expected[i][2]);
            assertThat(((Number) row.get("attack")).intValue()).as("attack at index %d", i).isEqualTo(expected[i][3]);
            assertThat(((Number) row.get("level_required")).intValue()).as("level_required at index %d", i).isEqualTo(expected[i][4]);
        }
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
