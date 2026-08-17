package com.starsbattle.testsupport;

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
 * <p>The container is a Testcontainers "singleton" (started once, statically,
 * never stopped explicitly) rather than a {@code @Testcontainers}-managed
 * {@code @Container}: since many unrelated test classes extend this base and
 * inherit the SAME static field, letting JUnit's per-class lifecycle stop it
 * after the first subclass finishes breaks every subclass that runs after —
 * Ryuk reaps the container when the whole JVM exits instead.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractDataJpaTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
