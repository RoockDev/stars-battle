package com.starsbattle.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Maven/Spring Boot skeleton actually wires together: the app context
 * starts, and the datasource resolves to the real, containerized Postgres 16
 * instance injected via @ServiceConnection (not some stale/default config).
 */
class SmokeContextLoadsIT extends AbstractPostgresIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void datasourceConnectsToContainerizedPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getURL()).contains(String.valueOf(POSTGRES.getMappedPort(5432)));
        }
    }
}
