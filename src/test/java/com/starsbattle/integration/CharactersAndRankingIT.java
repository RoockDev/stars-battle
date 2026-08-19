package com.starsbattle.integration;

import com.starsbattle.auth.service.JwtIssuer;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
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
 * Thin role-gating proof through the real security chain (spec: "List
 * Roster", "Ranking Query" — both require an authenticated USER or ADMIN).
 * Ordering/mapping/limit-clamp business logic is already covered by the
 * unit layer ({@code CharacterServiceTest}, {@code UserRankingServiceTest})
 * and the persistence layer ({@code CharacterRepositoryTest},
 * {@code UserRepositoryTest}) — this class only confirms the endpoints are
 * actually wired behind {@code @PreAuthorize("hasAnyRole('USER','ADMIN')")}.
 */
class CharactersAndRankingIT extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Autowired
    private UserRepository userRepository;

    @Test
    void listCharactersWithoutTokenReturns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/characters", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listCharactersWithUserRoleReturnsFullRoster() {
        String token = jwtIssuer.issue(1L, "user@batalla.com", List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/characters", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        List<Map<String, Object>> roster = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(roster).hasSize(12);
        assertThat(roster.get(0)).containsEntry("name", "Luke Skywalker");
    }

    @Test
    void listCharactersWithRoleOutsideUserOrAdminReturns403() {
        String token = jwtIssuer.issue(2L, "guest@batalla.com", List.of("GUEST"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/characters", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rankingWithoutTokenReturns401() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/users/ranking", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rankingWithAdminRoleIsReachable() {
        String token = jwtIssuer.issue(3L, "admin@batalla.com", List.of("ADMIN"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/users/ranking", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    @Test
    void rankingResponseOmitsEmailForEveryRankedPlayer() {
        userRepository.save(new User("ranked-player@batalla.com", "hashed-password"));
        String token = jwtIssuer.issue(4L, "viewer@batalla.com", List.of("USER"));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/users/ranking", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(ranking).isNotEmpty();
        assertThat(ranking).allSatisfy(row -> assertThat(row).doesNotContainKey("email"));
    }
}
