package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-ish unit tests (no Spring context, no mocks needed — real entity
 * graphs built directly) for {@code GET /battles/:id} authorization (spec:
 * "Room Join Authorization" reuses the same participant-or-admin shape as
 * the battle view authorization).
 */
class BattleAccessCheckerTest {

    private final BattleAccessChecker checker = new BattleAccessChecker();

    @Test
    void adminCanViewAnyBattleRegardlessOfParticipation() {
        Battle battle = battleWithInitiator(1L);

        assertThatCode(() -> checker.assertCanView(battle, 999L, true)).doesNotThrowAnyException();
    }

    @Test
    void initiatorCanViewOwnBattle() {
        Battle battle = battleWithInitiator(1L);

        assertThatCode(() -> checker.assertCanView(battle, 1L, false)).doesNotThrowAnyException();
    }

    @Test
    void opponentCanViewBattleTheyJoined() {
        Battle battle = battleWithInitiator(1L);
        setOpponent(battle, 2L);

        assertThatCode(() -> checker.assertCanView(battle, 2L, false)).doesNotThrowAnyException();
    }

    @Test
    void nonParticipantIsRejectedWithForbidden() {
        Battle battle = battleWithInitiator(1L);
        setOpponent(battle, 2L);

        assertThatThrownBy(() -> checker.assertCanView(battle, 3L, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void isParticipantReturnsFalseWhenNoOpponentYet() {
        Battle battle = battleWithInitiator(1L);

        assertThat(checker.isParticipant(battle, 2L)).isFalse();
    }

    private Battle battleWithInitiator(Long initiatorId) {
        User initiator = userWithId(initiatorId);
        Character character = new Character("Luke Skywalker", 100, 100, 20, 1);
        return new Battle(BattleMode.PVP, initiator, character);
    }

    private void setOpponent(Battle battle, Long opponentId) {
        battle.setOpponentUser(userWithId(opponentId));
    }

    private User userWithId(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}
