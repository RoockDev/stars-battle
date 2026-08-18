package com.starsbattle.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JWT config fails fast at startup when unconfigured, too short
 * for HS256, or non-positive (deliberate deviation from the source, which
 * fell back to a hardcoded insecure default and had no expiration
 * validation at all). Uses {@link ApplicationContextRunner} instead of the
 * full app context so this stays a fast unit-layer test — no datasource
 * needed.
 */
class JwtPropertiesFailFastTest {

    private static final String VALID_SECRET = "a-valid-secret-that-is-at-least-32-bytes-long";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextFailsToStartWhenSecretIsBlank() {
        contextRunner.withPropertyValues("jwt.secret=", "jwt.expiration=1h")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFailsToStartWhenSecretIsTooShortForHs256() {
        contextRunner.withPropertyValues("jwt.secret=too-short", "jwt.expiration=1h")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFailsToStartWhenExpirationIsZero() {
        contextRunner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.expiration=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextFailsToStartWhenExpirationIsNegative() {
        contextRunner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.expiration=-PT1H")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextStartsWhenSecretIsProvided() {
        contextRunner.withPropertyValues("jwt.secret=" + VALID_SECRET, "jwt.expiration=1h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.secret()).isEqualTo(VALID_SECRET);
                });
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }
}
