package com.starsbattle.integration;

import com.starsbattle.auth.service.JwtIssuer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

/**
 * Full-stack proof of everything gated behind the {@code dev} profile
 * (proposal decision #1, design D9/D-devtools, spec: "Dev Seed and Reset"):
 * {@code spring.flyway.clean-disabled} resolves to {@code false} ONLY here
 * (never in {@link DefaultProfileDevResetIT}'s default/{@code test}-only
 * context), the demo accounts from {@code V900__dev_demo_accounts.sql} are
 * actually seeded, and the reset endpoint is role-gated + wired to the real
 * {@link Flyway} bean end to end.
 *
 * <p>{@code @ActiveProfiles("dev")} combines with {@link AbstractPostgresIT}'s
 * inherited {@code "test"} profile (Spring's default {@code inheritProfiles
 * = true}), so this context activates both {@code test} (JWT secret) and
 * {@code dev} (extra Flyway seed location + clean-disabled override).
 *
 * <p>{@link Flyway#clean()} is stubbed to a no-op via a
 * {@link MockitoSpyBean} for the reset-wiring test only -- it must NEVER
 * actually run against the shared Testcontainers Postgres instance that
 * every other IT class in this suite reuses (it would drop every table for
 * the whole test JVM). {@link DevResetServiceTest} already proves the real
 * clean()-then-migrate() ordering against a fully mocked {@code Flyway} with
 * zero database risk; this class only proves the HTTP -&gt; security ->
 * controller -&gt; service wiring reaches the real bean.
 */
@ActiveProfiles("dev")
class DevProfileConfigurationIT extends AbstractPostgresIT {

    @Autowired
    private FlywayProperties flywayProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @MockitoSpyBean
    private Flyway flyway;

    @Test
    void cleanDisabledIsFalseUnderDevProfile() {
        assertThat(flywayProperties.isCleanDisabled()).isFalse();
    }

    @Test
    void devSeedDemoAccountsExistWithCorrectRoles() {
        Map<String, Object> admin = jdbcTemplate.queryForMap(
                "SELECT r.name AS role_name FROM users u "
                        + "JOIN user_roles ur ON ur.user_id = u.id "
                        + "JOIN roles r ON r.id = ur.role_id "
                        + "WHERE u.email = 'admin@batalla.com'");
        assertThat(admin.get("role_name")).isEqualTo("ADMIN");

        List<String> userEmails = List.of(
                "user1@batalla.com", "user2@batalla.com", "user3@batalla.com",
                "user4@batalla.com", "user5@batalla.com");
        for (String email : userEmails) {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT r.name AS role_name FROM users u "
                            + "JOIN user_roles ur ON ur.user_id = u.id "
                            + "JOIN roles r ON r.id = ur.role_id "
                            + "WHERE u.email = ?",
                    email);
            assertThat(row.get("role_name")).isEqualTo("USER");
        }
    }

    @Test
    void resetEndpointRejectsNonAdminWith403() {
        String token = jwtIssuer.issue(1L, "user1@batalla.com", List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/dev/reset", HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resetEndpointAllowsAdminAndInvokesFlywayCleanThenMigrateInOrder() {
        doReturn(null).when(flyway).clean();
        doReturn(null).when(flyway).migrate();

        String token = jwtIssuer.issue(2L, "admin@batalla.com", List.of("ADMIN"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/dev/reset", HttpMethod.POST, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);

        InOrder order = inOrder(flyway);
        order.verify(flyway, times(1)).clean();
        order.verify(flyway, times(1)).migrate();
    }
}
