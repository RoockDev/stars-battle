package com.starsbattle.battles.domain;

import com.starsbattle.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests, zero Spring context — {@link BattleRules} guards are
 * framework-free (spec: battle creation/PVP join validation rules).
 */
class BattleRulesTest {

    @Test
    void assertDistinctCharactersRejectsEqualIds() {
        assertThatThrownBy(() -> BattleRules.assertDistinctCharacters(1L, 1L, "same character"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("same character");
    }

    @Test
    void assertDistinctCharactersAllowsDifferentIds() {
        assertThatCode(() -> BattleRules.assertDistinctCharacters(1L, 2L, "same character"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertLevelGateRejectsInsufficientLevel() {
        assertThatThrownBy(() -> BattleRules.assertLevelGate(1, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("3")
                .hasMessageContaining("1");
    }

    @Test
    void assertLevelGateAllowsEqualLevel() {
        assertThatCode(() -> BattleRules.assertLevelGate(3, 3)).doesNotThrowAnyException();
    }

    @Test
    void assertLevelGateAllowsHigherLevel() {
        assertThatCode(() -> BattleRules.assertLevelGate(5, 3)).doesNotThrowAnyException();
    }

    @Test
    void assertModeRejectsMismatch() {
        assertThatThrownBy(() -> BattleRules.assertMode(BattleMode.PVE, BattleMode.PVP, "wrong mode"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("wrong mode");
    }

    @Test
    void assertModeAllowsMatch() {
        assertThatCode(() -> BattleRules.assertMode(BattleMode.PVP, BattleMode.PVP, "wrong mode"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertStatusRejectsMismatch() {
        assertThatThrownBy(() -> BattleRules.assertStatus(BattleStatus.FINISHED, BattleStatus.WAITING, "not waiting"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("not waiting");
    }

    @Test
    void assertStatusAllowsMatch() {
        assertThatCode(() -> BattleRules.assertStatus(BattleStatus.WAITING, BattleStatus.WAITING, "not waiting"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertNoOpponentYetRejectsWhenOpponentPresent() {
        assertThatThrownBy(() -> BattleRules.assertNoOpponentYet(true, "already has opponent"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("already has opponent");
    }

    @Test
    void assertNoOpponentYetAllowsWhenAbsent() {
        assertThatCode(() -> BattleRules.assertNoOpponentYet(false, "already has opponent"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertActorIsNotInitiatorRejectsSameUser() {
        assertThatThrownBy(() -> BattleRules.assertActorIsNotInitiator(7L, 7L, "own battle"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("own battle");
    }

    @Test
    void assertActorIsNotInitiatorAllowsDifferentUser() {
        assertThatCode(() -> BattleRules.assertActorIsNotInitiator(7L, 8L, "own battle"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertInProgressForTurnRejectsFinishedWithFinishedMessage() {
        assertThatThrownBy(() -> BattleRules.assertInProgressForTurn(BattleStatus.FINISHED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ha finalizado");
    }

    @Test
    void assertInProgressForTurnRejectsWaitingWithDistinctMessage() {
        assertThatThrownBy(() -> BattleRules.assertInProgressForTurn(BattleStatus.WAITING))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta en progreso");
    }

    @Test
    void assertInProgressForTurnAllowsInProgress() {
        assertThatCode(() -> BattleRules.assertInProgressForTurn(BattleStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }
}
