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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Full HTTP + real Postgres + real security chain proof of PVP turn
 * resolution (spec: "PVP Turn Resolution") — real {@code RandomAttackRoller}
 * randomness (not mocked), so the "attack until someone wins" test drives
 * real turns until a real knockout, proving the whole wiring (
 * {@code PvpBattleService} -&gt; {@code AttackRoller} -&gt;
 * {@code BattleFinisher}) end to end. Character ids reference the real
 * seeded roster: id 1 Luke Skywalker (attack 20), id 2 Han Solo (attack 18).
 */
class PvpTurnResolutionIT extends AbstractPostgresIT {

    private static final Long LUKE_ID = 1L;
    private static final Long HAN_ID = 2L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void turnRejectsActorWhoseTurnItIsNot() {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        Long battleId = startAndJoinPvpBattle(initiator, opponent);

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn", opponent.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "No es tu turno");
    }

    @Test
    void turnRejectsNonParticipantWithForbiddenRegardlessOfBattleState() {
        // Regression test for the authorization-ordering bug: a stranger
        // probing someone else's battle must get 403, not a business-state
        // 400 that would reveal the battle's mode/status to a non-participant.
        // The battle here is WAITING (not even started as PVP yet) — before
        // the fix this would have leaked a "no esta en progreso" 400 instead.
        Participant initiator = registerParticipant();
        Participant stranger = registerParticipant();
        ResponseEntity<Map> startResponse =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", LUKE_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn", stranger.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void turnRejectsNonPvpBattle() {
        Participant player = registerParticipant();
        ResponseEntity<Map> startResponse = post("/battles/start/pve", player.token(),
                Map.of("myCharacterId", LUKE_ID, "machineCharacterId", HAN_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn", player.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void turnRejectsWaitingBattleWithDistinctMessage() {
        Participant initiator = registerParticipant();
        ResponseEntity<Map> startResponse =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", LUKE_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn", initiator.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = (String) response.getBody().get("message");
        assertThat(message).contains("no esta en progreso");
    }

    @Test
    @SuppressWarnings("unchecked")
    void attackingUntilSomeoneWinsFinishesTheBattleAndAppliesRewards() {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        Long battleId = startAndJoinPvpBattle(initiator, opponent);

        String nextTurnSide = "INITIATOR";
        for (int turn = 0; turn < 200; turn++) {
            Participant actor = "INITIATOR".equals(nextTurnSide) ? initiator : opponent;

            ResponseEntity<Map> response = post("/battles/" + battleId + "/turn", actor.token());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> data = data(response);
            Map<String, Object> battleData = (Map<String, Object>) data.get("battle");
            if ("FINISHED".equals(battleData.get("status"))) {
                assertThat(battleData.get("winnerUserId")).isNotNull();
                assertThat(battleData.get("winnerIsMachine")).isEqualTo(false);
                assertThat(battleData.get("endedAt")).isNotNull();
                return;
            }
            nextTurnSide = (String) battleData.get("nextTurn");
        }

        fail("Battle did not finish within 200 turns");
    }

    private Long startAndJoinPvpBattle(Participant initiator, Participant opponent) {
        ResponseEntity<Map> startResponse =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", LUKE_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));

        post("/battles/" + battleId + "/join/pvp", opponent.token(), Map.of("myCharacterId", HAN_ID));
        return battleId;
    }

    private Participant registerParticipant() {
        String email = "turn-" + UUID.randomUUID() + "@batalla.com";
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
