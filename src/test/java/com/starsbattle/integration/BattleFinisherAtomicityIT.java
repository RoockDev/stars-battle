package com.starsbattle.integration;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
import com.starsbattle.websocket.BattleEventBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Real-transaction proof that {@code BattleFinisher}'s battle-close and
 * reward-application writes commit (or discard) together — a mocked
 * -repository unit test cannot exercise a real transaction boundary, so
 * this fills that gap with a real Postgres database (design: "Data Flow —
 * turn request" atomicity guarantee, the Prisma {@code $transaction}
 * equivalent).
 *
 * <p>Also doubles as the real-transaction proof for the WebSocket
 * broadcast-on-commit wiring (PR13, design D8): {@link SimpMessagingTemplate}
 * is mocked via {@link MockitoBean} so the two extra tests below can assert
 * {@code BattleEventBroadcaster} — a
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} — actually
 * fires exactly once after a real commit, and never after a real rollback,
 * without a flaky end-to-end STOMP socket round trip.
 */
class BattleFinisherAtomicityIT extends AbstractPostgresIT {

    @Autowired
    private BattleFinisherAtomicityHarness harness;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private BattleRepository battleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Test
    void commitPersistsBattleCloseAndRewardsTogether() {
        User winner = persistUser("finisher-winner-1@batalla.com");
        User loser = persistUser("finisher-loser-1@batalla.com");
        Character character = firstSeededCharacter();
        Battle battle = battleRepository.saveAndFlush(new Battle(BattleMode.PVP, winner, character));

        harness.finishAndCommit(battle, winner, loser);

        Battle reloadedBattle = battleRepository.findById(battle.getId()).orElseThrow();
        assertThat(reloadedBattle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(reloadedBattle.getEndedAt()).isNotNull();
        assertThat(reloadedBattle.getWinnerUser().getId()).isEqualTo(winner.getId());

        User reloadedWinner = userRepository.findById(winner.getId()).orElseThrow();
        User reloadedLoser = userRepository.findById(loser.getId()).orElseThrow();
        assertThat(reloadedWinner.getWins()).isEqualTo(1);
        assertThat(reloadedWinner.getXp()).isEqualTo(10);
        assertThat(reloadedLoser.getLosses()).isEqualTo(1);
    }

    @Test
    void rollbackDiscardsBattleCloseAndRewardsTogether() {
        User winner = persistUser("finisher-winner-2@batalla.com");
        User loser = persistUser("finisher-loser-2@batalla.com");
        Character character = firstSeededCharacter();
        Battle battle = battleRepository.saveAndFlush(new Battle(BattleMode.PVP, winner, character));

        assertThatThrownBy(() -> harness.finishThenForceRollback(battle, winner, loser))
                .isInstanceOf(IllegalStateException.class);

        Battle reloadedBattle = battleRepository.findById(battle.getId()).orElseThrow();
        assertThat(reloadedBattle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThat(reloadedBattle.getEndedAt()).isNull();
        assertThat(reloadedBattle.getWinnerUser()).isNull();

        User reloadedWinner = userRepository.findById(winner.getId()).orElseThrow();
        User reloadedLoser = userRepository.findById(loser.getId()).orElseThrow();
        assertThat(reloadedWinner.getWins()).isEqualTo(0);
        assertThat(reloadedWinner.getXp()).isEqualTo(0);
        assertThat(reloadedLoser.getLosses()).isEqualTo(0);
    }

    @Test
    void commitBroadcastsBattleFinishedEventAfterCommit() {
        User winner = persistUser("finisher-ws-winner-1@batalla.com");
        User loser = persistUser("finisher-ws-loser-1@batalla.com");
        Character character = firstSeededCharacter();
        Battle battle = battleRepository.saveAndFlush(new Battle(BattleMode.PVP, winner, character));

        harness.finishAndCommit(battle, winner, loser);

        verify(simpMessagingTemplate).convertAndSend(eq("/topic/battles/" + battle.getId()), any(Object.class));
    }

    @Test
    void rollbackNeverBroadcastsBattleFinishedEvent() {
        User winner = persistUser("finisher-ws-winner-2@batalla.com");
        User loser = persistUser("finisher-ws-loser-2@batalla.com");
        Character character = firstSeededCharacter();
        Battle battle = battleRepository.saveAndFlush(new Battle(BattleMode.PVP, winner, character));

        assertThatThrownBy(() -> harness.finishThenForceRollback(battle, winner, loser))
                .isInstanceOf(IllegalStateException.class);

        verify(simpMessagingTemplate, never()).convertAndSend(eq("/topic/battles/" + battle.getId()), any(Object.class));
    }

    private User persistUser(String email) {
        return userRepository.saveAndFlush(new User(email, "hashed-password"));
    }

    private Character firstSeededCharacter() {
        return characterRepository.findAllByOrderByIdAsc().get(0);
    }
}
