package com.starsbattle.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base class for full-stack integration tests. Boots the Spring context
 * against a real, disposable Postgres 16 container (migrations are Postgres SQL,
 * so H2 is not an acceptable substitute anywhere in this codebase).
 *
 * <p>The container is a Testcontainers "singleton" (started once, statically,
 * never stopped explicitly) rather than a {@code @Testcontainers}-managed
 * {@code @Container}: multiple unrelated test classes extend this base and
 * inherit the SAME static field, so letting JUnit's per-class lifecycle stop
 * it after the first subclass finishes breaks every subclass that runs after
 * — Ryuk reaps the container when the whole JVM exits instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractPostgresIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
