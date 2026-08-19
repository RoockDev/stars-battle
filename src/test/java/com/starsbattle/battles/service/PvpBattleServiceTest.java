package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.AttackLevel;
import com.starsbattle.battles.domain.AttackRoll;
import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.domain.BattleTurn;
import com.starsbattle.battles.dto.TurnResultView;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repository/collaborators, no Spring context — spec:
 * "PVP Turn Resolution". {@code AttackRoller} and {@code BattleFinisher} are
 * mocked here (their own behavior is proven by {@code RandomAttackRollerTest}
 * and {@code BattleFinisherTest}); the real wiring end to end is covered by
 * {@code PvpTurnResolutionIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PvpBattleServiceTest {

    private static final Long BATTLE_ID = 42L;
    private static final Long INITIATOR_ID = 1L;
    private static final Long OPPONENT_ID = 2L;

    @Mock
    private BattleRepository battleRepository;

    @Mock
    private AttackRoller attackRoller;

    @Mock
    private BattleFinisher battleFinisher;

    private PvpBattleService pvpBattleService;

    @BeforeEach
    void setUp() {
        pvpBattleService = new PvpBattleService(battleRepository, attackRoller, battleFinisher);
        when(battleRepository.save(any(Battle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void turnThrowsNotFoundWhenBattleMissing() {
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void turnRejectsNonPvpMode() {
        Battle battle = pveBattle();
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void turnRejectsFinishedBattleWithFinishedMessage() {
        Battle battle = pvpBattle(BattleStatus.FINISHED, BattleTurn.INITIATOR, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ha finalizado");
    }

    @Test
    void turnRejectsWaitingBattleWithDistinctMessage() {
        Battle battle = pvpBattle(BattleStatus.WAITING, BattleTurn.INITIATOR, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta en progreso");
    }

    @Test
    void turnRejectsWhenActorIsNotTheOneWhoseTurnItIs() {
        Battle battle = pvpBattle(BattleStatus.IN_PROGRESS, BattleTurn.INITIATOR, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pvpBattleService.applyTurn(OPPONENT_ID, BATTLE_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void turnRejectsNonParticipantActor() {
        Battle battle = pvpBattle(BattleStatus.IN_PROGRESS, BattleTurn.INITIATOR, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pvpBattleService.applyTurn(999L, BATTLE_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void turnAppliesDamageIncrementsTurnAndFlipsNextTurnWhenBothSurvive() {
        Battle battle = pvpBattle(BattleStatus.IN_PROGRESS, BattleTurn.INITIATOR, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(20)).thenReturn(new AttackRoll(AttackLevel.NORMAL, 20));

        TurnResultView result = pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID);

        assertThat(battle.getOpponentCurrentHp()).isEqualTo(80);
        assertThat(battle.getTurnNumber()).isEqualTo(2);
        assertThat(battle.getNextTurn()).isEqualTo(BattleTurn.OPPONENT);
        assertThat(result.attackerSide()).isEqualTo(BattleTurn.INITIATOR);
        assertThat(result.baseAttack()).isEqualTo(20);
        assertThat(result.rolledAttack()).isEqualTo(20);
        assertThat(result.damage()).isEqualTo(20);
        assertThat(result.battle().status()).isEqualTo(BattleStatus.IN_PROGRESS);
        verify(battleFinisher, never()).finishWithHumanWinner(any(), any(), any());
    }

    @Test
    void turnFloorsDamageAtZeroHpAndFinishesWhenDefenderKnockedOut() {
        Battle battle = pvpBattle(BattleStatus.IN_PROGRESS, BattleTurn.INITIATOR, 100, 15);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(20)).thenReturn(new AttackRoll(AttackLevel.CRITICO, 30));
        when(battleFinisher.finishWithHumanWinner(eq(battle), eq(battle.getInitiatorUser()), eq(battle.getOpponentUser())))
                .thenAnswer(invocation -> {
                    battle.setStatus(BattleStatus.FINISHED);
                    return battle;
                });

        TurnResultView result = pvpBattleService.applyTurn(INITIATOR_ID, BATTLE_ID);

        assertThat(battle.getOpponentCurrentHp()).isEqualTo(0);
        assertThat(result.damage()).isEqualTo(30);
        assertThat(result.battle().status()).isEqualTo(BattleStatus.FINISHED);
        verify(battleFinisher).finishWithHumanWinner(battle, battle.getInitiatorUser(), battle.getOpponentUser());
        verify(battleRepository, never()).save(any(Battle.class));
    }

    @Test
    void turnFinishesWithOpponentAsWinnerWhenInitiatorKnockedOut() {
        Battle battle = pvpBattle(BattleStatus.IN_PROGRESS, BattleTurn.OPPONENT, 10, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(18)).thenReturn(new AttackRoll(AttackLevel.CRITICO, 27));
        when(battleFinisher.finishWithHumanWinner(eq(battle), eq(battle.getOpponentUser()), eq(battle.getInitiatorUser())))
                .thenAnswer(invocation -> {
                    battle.setStatus(BattleStatus.FINISHED);
                    return battle;
                });

        TurnResultView result = pvpBattleService.applyTurn(OPPONENT_ID, BATTLE_ID);

        assertThat(battle.getInitiatorCurrentHp()).isEqualTo(0);
        assertThat(result.attackerSide()).isEqualTo(BattleTurn.OPPONENT);
        verify(battleFinisher).finishWithHumanWinner(battle, battle.getOpponentUser(), battle.getInitiatorUser());
    }

    private Battle pveBattle() {
        User initiator = userWithId(INITIATOR_ID);
        Character character = characterWithAttack(20);
        Battle battle = new Battle(BattleMode.PVE, initiator, character);
        battle.setStatus(BattleStatus.IN_PROGRESS);
        return battle;
    }

    private Battle pvpBattle(BattleStatus status, BattleTurn nextTurn, int initiatorHp, int opponentHp) {
        User initiator = userWithId(INITIATOR_ID);
        User opponent = userWithId(OPPONENT_ID);
        Character initiatorCharacter = characterWithAttack(20);
        Character opponentCharacter = characterWithAttack(18);

        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setOpponentUser(opponent);
        battle.setOpponentCharacter(opponentCharacter);
        battle.setStatus(status);
        battle.setNextTurn(nextTurn);
        battle.setInitiatorCurrentHp(initiatorHp);
        battle.setOpponentCurrentHp(opponentHp);
        return battle;
    }

    private User userWithId(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private Character characterWithAttack(int attack) {
        Character character = mock(Character.class);
        when(character.getAttack()).thenReturn(attack);
        return character;
    }
}
