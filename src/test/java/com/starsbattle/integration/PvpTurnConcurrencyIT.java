package com.starsbattle.integration;

import com.starsbattle.auth.dto.RegisterRequest;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8.2 (deferred twice -- batches 4 and 5): a dedicated race proving
 * {@code OptimisticLockingFailureException} actually maps to 409 from a
 * REAL concurrent write on {@code PvpBattleService.applyTurn}'s code path,
 * not just the mapping table itself ({@code GlobalExceptionHandler}, already
 * covered elsewhere; that mapping exists since PR4 and is exercised
 * indirectly by {@code BattleCreationService.joinPvp}'s documented
 * non-swallowing comment, but nothing previously forced two concurrent turn
 * writes against the SAME battle).
 *
 * <p>Both requests are fired by the SAME acting participant (the current
 * {@code nextTurn} owner) so both pass the turn-order guard when they each
 * independently read the battle -- the race is on the {@code @Version}
 * check at flush, not on "whose turn is it". Luke Skywalker (attack 20, max
 * roll 30 at the CRITICO tier) attacking Han Solo (hp 90) cannot finish the
 * battle in a single hit, so neither concurrent request can hit the
 * {@code BattleFinisher} branch and complicate the assertion.
 */
class PvpTurnConcurrencyIT extends AbstractPostgresIT {

    private static final Long LUKE_ID = 1L;
    private static final Long HAN_ID = 2L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void concurrentTurnRequestsOnSameBattleYieldOneSuccessAndOneConflict() throws Exception {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        Long battleId = startAndJoinPvpBattle(initiator, opponent);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<HttpStatus> fireTurn = () -> {
            bothReady.countDown();
            go.await();
            return HttpStatus.valueOf(post("/battles/" + battleId + "/turn", initiator.token())
                    .getStatusCode().value());
        };

        try {
            Future<HttpStatus> first = executor.submit(fireTurn);
            Future<HttpStatus> second = executor.submit(fireTurn);

            bothReady.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<HttpStatus> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        } finally {
            executor.shutdownNow();
        }
    }

    private Long startAndJoinPvpBattle(Participant initiator, Participant opponent) {
        ResponseEntity<Map> startResponse =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", LUKE_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));

        post("/battles/" + battleId + "/join/pvp", opponent.token(), Map.of("myCharacterId", HAN_ID));
        return battleId;
    }

    private Participant registerParticipant() {
        String email = "concurrency-" + UUID.randomUUID() + "@batalla.com";
        ResponseEntity<Map> response =
                restTemplate.postForEntity("/auth/register", new RegisterRequest(email, "force123"), Map.class);
        return new Participant((String) data(response).get("access_token"));
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> post(String path, String token) {
        return post(path, token, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private record Participant(String token) {
    }
}
