package com.starsbattle.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JWT secret fails fast at startup when unconfigured (deliberate
 * deviation from the source, which fell back to a hardcoded insecure
 * default). Uses {@link ApplicationContextRunner} instead of the full app
 * context so this stays a fast unit-layer test — no datasource needed.
 */
class JwtPropertiesFailFastTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void contextFailsToStartWhenSecretIsBlank() {
        contextRunner.withPropertyValues("jwt.secret=", "jwt.expiration=1h")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextStartsWhenSecretIsProvided() {
        contextRunner.withPropertyValues("jwt.secret=a-valid-non-blank-secret", "jwt.expiration=1h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.secret()).isEqualTo("a-valid-non-blank-secret");
                });
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }
}
