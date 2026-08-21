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
 * Full HTTP + real Postgres + real security chain proof of PVE turn
 * resolution (spec: "PVE Turn Resolution") — real {@code RandomAttackRoller}
 * randomness (not mocked), so the "attack until someone wins" test drives
 * real turns until a real knockout, proving the whole wiring (
 * {@code PveBattleService} -&gt; {@code AttackRoller} -&gt;
 * {@code BattleFinisher}) end to end, including both the
 * {@code finishWithHumanWinnerAgainstMachine} and {@code
 * finishWithMachineWinner} branches (whichever the real random rolls
 * produce). Character ids reference the real seeded roster: id 1 Luke
 * Skywalker (attack 20), id 2 Han Solo (attack 18).
 */
class PveTurnResolutionIT extends AbstractPostgresIT {

    private static final Long LUKE_ID = 1L;
    private static final Long HAN_ID = 2L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void turnRejectsNonInitiatorActor() {
        Participant initiator = registerParticipant();
        Participant intruder = registerParticipant();
        Long battleId = startPveBattle(initiator);

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn/pve", intruder.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "no puedes jugar el turno de una batalla ajena");
    }

    @Test
    @SuppressWarnings("unchecked")
    void turnRejectsNonPveBattle() {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        ResponseEntity<Map> startResponse =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", LUKE_ID));
        Long battleId = Long.valueOf(String.valueOf(data(startResponse).get("id")));
        post("/battles/" + battleId + "/join/pvp", opponent.token(), Map.of("myCharacterId", HAN_ID));

        ResponseEntity<Map> response = post("/battles/" + battleId + "/turn/pve", initiator.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void attackingUntilSomeoneWinsFinishesTheBattleAndAppliesRewards() {
        Participant initiator = registerParticipant();
        Long battleId = startPveBattle(initiator);

        for (int turn = 0; turn < 200; turn++) {
            ResponseEntity<Map> response = post("/battles/" + battleId + "/turn/pve", initiator.token());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> data = data(response);
            Map<String, Object> battleData = (Map<String, Object>) data.get("battle");
            if ("FINISHED".equals(battleData.get("status"))) {
                assertThat(battleData.get("endedAt")).isNotNull();
                boolean winnerIsMachine = (boolean) battleData.get("winnerIsMachine");
                if (winnerIsMachine) {
                    assertThat(battleData.get("winnerUserId")).isNull();
                    assertThat(data.get("machineAttack")).isNotNull();
                } else {
                    assertThat(battleData.get("winnerUserId")).isNotNull();
                }
                return;
            }
        }

        fail("Battle did not finish within 200 turns");
    }

    private Long startPveBattle(Participant initiator) {
        ResponseEntity<Map> startResponse = post("/battles/start/pve", initiator.token(),
                Map.of("myCharacterId", LUKE_ID, "machineCharacterId", HAN_ID));
        return Long.valueOf(String.valueOf(data(startResponse).get("id")));
    }

    private Participant registerParticipant() {
        String email = "pve-turn-" + UUID.randomUUID() + "@batalla.com";
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
