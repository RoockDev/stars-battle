package com.starsbattle.integration;

import com.starsbattle.auth.dto.RegisterRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full HTTP + real Postgres + real security chain proof of battle creation,
 * PVP join, and the battle view authorization (spec: "Start PVE Battle",
 * "Start PVP Battle", "Join PVP Battle", and {@code GET /battles/:id}).
 * Character ids reference the real seeded roster (V3__characters_roster.sql):
 * id 1 Luke Skywalker (levelRequired 1), id 2 Han Solo (levelRequired 1),
 * id 3 Leia Organa (levelRequired 1), id 4 Obi-Wan Kenobi (levelRequired 2).
 * Freshly-registered users default to level 1.
 */
class BattleCreationAndQueryIT extends AbstractPostgresIT {

    private static final Long LUKE_ID = 1L;
    private static final Long HAN_ID = 2L;
    private static final Long LEIA_ID = 3L;
    private static final Long OBI_WAN_ID = 4L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    void startPveCreatesInProgressBattleWithBothHpSet() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = post("/battles/start/pve", player.token(),
                Map.of("myCharacterId", LUKE_ID, "machineCharacterId", HAN_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = data(response);
        assertThat(data.get("mode")).isEqualTo("PVE");
        assertThat(data.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(data.get("turnNumber")).isEqualTo(1);
        assertThat(data.get("nextTurn")).isEqualTo("INITIATOR");
        assertThat(((Map) data.get("initiatorCharacter")).get("name")).isEqualTo("Luke Skywalker");
        assertThat(((Map) data.get("opponentCharacter")).get("name")).isEqualTo("Han Solo");
    }

    @Test
    void startPveRejectsSameCharacterIds() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = post("/battles/start/pve", player.token(),
                Map.of("myCharacterId", LUKE_ID, "machineCharacterId", LUKE_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void startPveRejectsInsufficientLevel() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = post("/battles/start/pve", player.token(),
                Map.of("myCharacterId", OBI_WAN_ID, "machineCharacterId", LUKE_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = (String) response.getBody().get("message");
        assertThat(message).contains("2").contains("1");
    }

    @Test
    void startPveReturns404WhenMachineCharacterMissing() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = post("/battles/start/pve", player.token(),
                Map.of("myCharacterId", LUKE_ID, "machineCharacterId", 999999));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void startPvpCreatesWaitingBattleWithNoOpponent() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = post("/battles/start/pvp", player.token(), Map.of("myCharacterId", LUKE_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = data(response);
        assertThat(data.get("mode")).isEqualTo("PVP");
        assertThat(data.get("status")).isEqualTo("WAITING");
        assertThat(data.get("opponentUser")).isNull();
        assertThat(data.get("opponentCharacter")).isNull();
    }

    @Test
    void joinPvpTransitionsBattleToInProgress() {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);

        ResponseEntity<Map> response = post("/battles/" + battleId + "/join/pvp", opponent.token(),
                Map.of("myCharacterId", HAN_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = data(response);
        assertThat(data.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(((Map) data.get("opponentCharacter")).get("name")).isEqualTo("Han Solo");
    }

    @Test
    void joinPvpRejectsInitiatorJoiningOwnBattle() {
        Participant initiator = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);

        ResponseEntity<Map> response = post("/battles/" + battleId + "/join/pvp", initiator.token(),
                Map.of("myCharacterId", HAN_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinPvpRejectsMirrorMatchCharacter() {
        Participant initiator = registerParticipant();
        Participant opponent = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);

        ResponseEntity<Map> response = post("/battles/" + battleId + "/join/pvp", opponent.token(),
                Map.of("myCharacterId", LUKE_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinPvpRejectsWhenOpponentAlreadyExists() {
        Participant initiator = registerParticipant();
        Participant firstOpponent = registerParticipant();
        Participant secondOpponent = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);
        post("/battles/" + battleId + "/join/pvp", firstOpponent.token(), Map.of("myCharacterId", HAN_ID));

        ResponseEntity<Map> response = post("/battles/" + battleId + "/join/pvp", secondOpponent.token(),
                Map.of("myCharacterId", LEIA_ID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getBattleAllowsInitiator() {
        Participant initiator = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);

        ResponseEntity<Map> response = get("/battles/" + battleId, initiator.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getBattleAllowsAdmin() {
        Participant initiator = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);
        String adminToken = jwtIssuer.issue(999999L, "admin-battle-view@batalla.com", List.of("ADMIN"));

        ResponseEntity<Map> response = get("/battles/" + battleId, adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getBattleRejectsNonParticipant() {
        Participant initiator = registerParticipant();
        Participant stranger = registerParticipant();
        Long battleId = startPvpBattle(initiator, LUKE_ID);

        ResponseEntity<Map> response = get("/battles/" + battleId, stranger.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getBattleReturns404WhenMissing() {
        Participant player = registerParticipant();

        ResponseEntity<Map> response = get("/battles/999999", player.token());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Long startPvpBattle(Participant initiator, Long characterId) {
        ResponseEntity<Map> response =
                post("/battles/start/pvp", initiator.token(), Map.of("myCharacterId", characterId));
        Map<String, Object> data = data(response);
        return Long.valueOf(String.valueOf(data.get("id")));
    }

    private Participant registerParticipant() {
        String email = "battle-" + UUID.randomUUID() + "@batalla.com";
        ResponseEntity<Map> response =
                restTemplate.postForEntity("/auth/register", new RegisterRequest(email, "force123"), Map.class);
        Map<String, Object> data = data(response);
        String token = (String) data.get("access_token");
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        Long userId = Long.valueOf(String.valueOf(user.get("id")));
        return new Participant(userId, token);
    }

    private ResponseEntity<Map> post(String path, String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private record Participant(Long userId, String token) {
    }
}
