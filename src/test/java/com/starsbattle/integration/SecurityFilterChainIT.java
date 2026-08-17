package com.starsbattle.integration;

import com.starsbattle.auth.service.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack proof of {@code SecurityConfig} (design D1-D3): permitAll
 * routes, envelope-shaped 401/403 bodies (D2 — not Spring's raw RFC-6750
 * bodies), and role-gated routes (D3) via the real filter chain + a minted
 * JWT from the real {@link JwtIssuer} bean.
 *
 * <p>The probe routes used to live in a standalone, top-level
 * {@code @RestController} under this package, which every
 * {@code @SpringBootTest} picked up via classpath component scanning —
 * unrelated tests (e.g. {@code FlywayBaselineMigrationIT}) inherited its
 * routes for no reason. Nested {@code @TestConfiguration} classes are
 * excluded from Spring Boot's default component scan, so declaring
 * {@link SecureProbeController} as a {@code @Bean} inside {@link ProbeConfig}
 * and {@code @Import}-ing it here scopes it to exactly this test.
 */
@Import(SecurityFilterChainIT.ProbeConfig.class)
class SecurityFilterChainIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    void permitAllAuthRegisterRouteIsReachableWithoutToken() {
        // No AuthController exists yet at this point in the port (it lands in
        // PR6), so this necessarily 404s rather than 200s — the point is that
        // it is NOT blocked by the security filter chain (i.e. not a 401).
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/register", Map.of(), Map.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void permitAllAuthLoginRouteIsReachableWithoutToken() {
        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/login", Map.of(), Map.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorHealthIsReachableWithoutToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiDocsAreReachableWithoutTokenAndNotWrappedInTheEnvelope() {
        // Discoverable API docs (springdoc-openapi) are deliberately public in
        // this portfolio repo, same rationale as /actuator/health: they only
        // describe the API surface, never real data. The response must also
        // NOT be wrapped in ApiResponse -- Swagger UI expects a raw OpenAPI
        // document, not {success, message, data}.
        ResponseEntity<Map> response = restTemplate.getForEntity("/v3/api-docs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("openapi");
        assertThat(response.getBody()).doesNotContainKey("success");
    }

    @Test
    void missingTokenOnProtectedRouteReturns401WithEnvelope() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/probe/secure", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "No autenticado");
    }

    @Test
    void invalidTokenOnProtectedRouteReturns401WithEnvelope() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this-is-not-a-valid-jwt");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/probe/secure", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "No autenticado");
    }

    @Test
    void validTokenWithUserRoleCanAccessAuthenticatedRoute() {
        String token = jwtIssuer.issue(1L, "user@batalla.com", List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/probe/secure", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void userRoleIsForbiddenFromAdminRouteWithEnvelope() {
        String token = jwtIssuer.issue(2L, "user2@batalla.com", List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/probe/admin", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("message", "No tienes permiso para realizar esta accion");
    }

    @Test
    void adminRoleCanAccessAdminRoute() {
        String token = jwtIssuer.issue(3L, "admin@batalla.com", List.of("ADMIN"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/probe/admin", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        SecureProbeController secureProbeController() {
            return new SecureProbeController();
        }
    }

    /**
     * Test-only controller used exclusively by {@link SecurityFilterChainIT}
     * (via {@link ProbeConfig}) to exercise the real {@code SecurityConfig}
     * end to end (401/403 envelope shapes, role-gated routes) — no real
     * feature controller exists yet at this point in the port
     * (auth/characters/battles land in PR6/PR7/PR9-12).
     */
    @RestController
    static class SecureProbeController {

        @GetMapping("/probe/secure")
        public Map<String, String> secureProbe() {
            return Map.of("value", "secure");
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/probe/admin")
        public Map<String, String> adminProbe() {
            return Map.of("value", "admin");
        }
    }
}
