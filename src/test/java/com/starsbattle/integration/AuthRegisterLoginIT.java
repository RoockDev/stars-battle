package com.starsbattle.integration;

import com.starsbattle.auth.dto.LoginRequest;
import com.starsbattle.auth.dto.RegisterRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full register -&gt; login flow through the real Spring Security filter
 * chain, real bcrypt hashing, and a real Postgres database (spec:
 * "Registration and Login" scenarios) — not mocks. Confirms the narrowed
 * SecurityConfig permitAll rule for {@code POST /auth/register}/
 * {@code POST /auth/login} actually lets these routes through, and that the
 * envelope + error mapping from #4/#5 apply end to end. Imports its own
 * nested {@link ProbeConfig} for {@code /probe/secure}, used at the end of
 * the flow to prove the minted access token actually authenticates against
 * a real protected route — nested here rather than shared with {@code
 * SecurityFilterChainIT}'s identical copy because Spring Boot's test {@code
 * TypeExcludeFilter} only exempts types nested within the CURRENTLY-RUNNING
 * {@code @SpringBootTest} class from component scanning (verified
 * empirically: hoisting this into the shared {@code AbstractPostgresIT}
 * base caused an "ambiguous mapping" duplicate-bean startup failure).
 */
@Import(AuthRegisterLoginIT.ProbeConfig.class)
class AuthRegisterLoginIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registerThenLoginReturnsAccessTokenAndUserSummaryWithUserRole() {
        String email = "luke-" + UUID.randomUUID() + "@batalla.com";

        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
                "/auth/register", new RegisterRequest(email, "force123"), Map.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registerBody = registerResponse.getBody();
        assertThat(registerBody).containsEntry("success", true);
        Map<String, Object> registerData = (Map<String, Object>) registerBody.get("data");
        assertThat(registerData.get("access_token")).isNotNull();
        Map<String, Object> registeredUser = (Map<String, Object>) registerData.get("user");
        assertThat(registeredUser.get("email")).isEqualTo(email);
        assertThat(registeredUser).doesNotContainKey("password");
        assertThat(registeredUser).doesNotContainKey("passwordHash");
        assertThat((Iterable<String>) registeredUser.get("roles")).containsExactly("USER");

        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "force123"), Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String accessToken = (String) loginData.get("access_token");
        assertThat(accessToken).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> probeResponse = restTemplate.exchange(
                "/probe/secure", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(probeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registerWithAlreadyUsedEmailReturns400() {
        String email = "duplicate-" + UUID.randomUUID() + "@batalla.com";
        restTemplate.postForEntity("/auth/register", new RegisterRequest(email, "force123"), Map.class);

        ResponseEntity<Map> secondAttempt = restTemplate.postForEntity(
                "/auth/register", new RegisterRequest(email, "another-password"), Map.class);

        assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondAttempt.getBody()).containsEntry("success", false);
    }

    @Test
    void loginWithWrongPasswordReturns401WithGenericMessage() {
        String email = "leia-" + UUID.randomUUID() + "@batalla.com";
        restTemplate.postForEntity("/auth/register", new RegisterRequest(email, "correct-password"), Map.class);

        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(email, "wrong-password"), Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse.getBody()).containsEntry("success", false);
        assertThat(loginResponse.getBody()).containsEntry("message", "Credenciales invalidas");
    }

    @Test
    void loginWithUnknownEmailReturns401WithSameGenericMessage() {
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("no-such-user@batalla.com", "whatever"), Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResponse.getBody()).containsEntry("message", "Credenciales invalidas");
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        SecureProbeController secureProbeController() {
            return new SecureProbeController();
        }
    }

    @RestController
    static class SecureProbeController {

        @GetMapping("/probe/secure")
        public Map<String, String> secureProbe() {
            return Map.of("value", "secure");
        }
    }
}
