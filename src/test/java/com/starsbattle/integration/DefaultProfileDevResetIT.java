package com.starsbattle.integration;

import com.starsbattle.auth.service.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the regression the design flagged as most important to guard
 * (design: "Flyway" / spec: "cleanDisabled outside dev"): outside the
 * {@code dev} profile, {@code spring.flyway.clean-disabled} stays at its
 * safe {@code true} default, and the reset endpoint bean does not exist at
 * all ({@code DevResetController} is {@code @Profile("dev")}), so the route
 * is genuinely unmapped rather than merely unauthorized.
 *
 * <p>This class extends {@link AbstractPostgresIT} with NO extra active
 * profile, i.e. only {@code test} (never {@code dev}) is active -- the
 * "default/prod-style profile" case from the spec scenario.
 */
class DefaultProfileDevResetIT extends AbstractPostgresIT {

    @Autowired
    private FlywayProperties flywayProperties;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    void cleanDisabledRemainsTrueOutsideDevProfile() {
        assertThat(flywayProperties.isCleanDisabled()).isTrue();
    }

    @Test
    void resetEndpointIsNotFoundOutsideDevProfile() {
        // Authenticated as ADMIN so the assertion proves the ROUTE itself is
        // absent (@Profile("dev") means the bean never registers), not just
        // that an unauthenticated/unauthorized caller was rejected earlier
        // in the filter chain.
        String token = jwtIssuer.issue(1L, "admin@batalla.com", List.of("ADMIN"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/dev/reset", HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
