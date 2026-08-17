package com.starsbattle.integration;

import com.starsbattle.auth.service.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Full-stack proof of {@code SecurityConfig} (design D1-D3): permitAll
 * routes, envelope-shaped 401/403 bodies (D2 — not Spring's raw RFC-6750
 * bodies), and role-gated routes (D3) via the real filter chain + a minted
 * JWT from the real {@link JwtIssuer} bean.
 */
class SecurityFilterChainIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    void permitAllAuthRouteIsReachableWithoutToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/auth/probe", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void actuatorHealthIsReachableWithoutToken() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
}
