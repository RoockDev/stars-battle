package com.starsbattle.devtools;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Wraps Flyway's own reset primitives (proposal decision #1) instead of
 * hand-rolling cascading deletes: {@code clean()} drops every object Flyway
 * manages, {@code migrate()} rebuilds the schema and reapplies the main
 * migration chain plus, under the {@code dev} profile only, the demo-account
 * seed. Only registered as a bean under {@code @Profile("dev")} so it (and
 * its controller) simply do not exist outside that profile.
 */
@Service
@Profile("dev")
public class DevResetService {

    private final Flyway flyway;

    public DevResetService(Flyway flyway) {
        this.flyway = flyway;
    }

    public void reset() {
        flyway.clean();
        flyway.migrate();
    }
}
