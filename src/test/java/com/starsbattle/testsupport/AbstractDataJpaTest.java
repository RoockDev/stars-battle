package com.starsbattle.testsupport;

import com.starsbattle.integration.AbstractPostgresIT;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for the persistence test slice. Uses a real Postgres 16
 * container (migrations are Postgres SQL) instead of the default embedded
 * database, so Flyway applies the real V1-V3 migrations before each test.
 *
 * <p>Reuses the exact same static container instance as
 * {@link AbstractPostgresIT} rather than starting a second one: referencing
 * {@code AbstractPostgresIT.POSTGRES} triggers that class's static
 * initializer (which starts the container) at most once per JVM, so the
 * {@code @DataJpaTest} slice and the full-stack {@code *IT} tests share one
 * running Postgres 16 container instead of paying for two.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractDataJpaTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = AbstractPostgresIT.POSTGRES;
}
