package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.AttackLevel;
import com.starsbattle.battles.domain.AttackRoll;
import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.dto.PveTurnResultView;
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
 * "PVE Turn Resolution". Covers the three outcomes (player wins immediately
 * with no machine counter, player loses after the machine's counter, both
 * survive) plus the turn-order/mode/status guards. {@code AttackRoller} and
 * {@code BattleFinisher} are mocked here — their own behavior is proven by
 * {@code RandomAttackRollerTest} and {@code BattleFinisherTest}; the real
 * wiring end to end is covered by {@code PveTurnResolutionIT}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PveBattleServiceTest {

    private static final Long BATTLE_ID = 42L;
    private static final Long INITIATOR_ID = 1L;
    private static final Long OTHER_USER_ID = 999L;

    @Mock
    private BattleRepository battleRepository;

    @Mock
    private AttackRoller attackRoller;

    @Mock
    private BattleFinisher battleFinisher;

    private PveBattleService pveBattleService;

    @BeforeEach
    void setUp() {
        pveBattleService = new PveBattleService(battleRepository, attackRoller, battleFinisher);
        when(battleRepository.save(any(Battle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void turnThrowsNotFoundWhenBattleMissing() {
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void turnRejectsNonPveMode() {
        Battle battle = pvpBattle(100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void turnRejectsFinishedBattleWithFinishedMessage() {
        Battle battle = pveBattle(BattleStatus.FINISHED, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ha finalizado");
    }

    @Test
    void turnRejectsNonInitiatorActor() {
        Battle battle = pveBattle(BattleStatus.IN_PROGRESS, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> pveBattleService.applyTurn(OTHER_USER_ID, BATTLE_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("no puedes jugar el turno de una batalla ajena");
    }

    @Test
    void playerWinsImmediatelyWithoutMachineCounterAttack() {
        Battle battle = pveBattle(BattleStatus.IN_PROGRESS, 100, 15);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(20)).thenReturn(new AttackRoll(AttackLevel.CRITICO, 1.5, 30));
        when(battleFinisher.finishWithHumanWinnerAgainstMachine(eq(battle), eq(battle.getInitiatorUser())))
                .thenAnswer(invocation -> {
                    battle.setStatus(BattleStatus.FINISHED);
                    return battle;
                });

        PveTurnResultView result = pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID);

        assertThat(battle.getOpponentCurrentHp()).isEqualTo(0);
        assertThat(result.playerAttack().damage()).isEqualTo(30);
        assertThat(result.machineAttack()).isNull();
        assertThat(result.battle().status()).isEqualTo(BattleStatus.FINISHED);
        verify(battleFinisher).finishWithHumanWinnerAgainstMachine(battle, battle.getInitiatorUser());
        verify(battleFinisher, never()).finishWithMachineWinner(any(), any());
        verify(attackRoller, never()).roll(18);
    }

    @Test
    void machineWinsAfterCounterAttackWhenPlayerDoesNotFinishItFirst() {
        Battle battle = pveBattle(BattleStatus.IN_PROGRESS, 10, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(20)).thenReturn(new AttackRoll(AttackLevel.NORMAL, 1.0, 20));
        when(attackRoller.roll(18)).thenReturn(new AttackRoll(AttackLevel.CRITICO, 1.5, 27));
        when(battleFinisher.finishWithMachineWinner(eq(battle), eq(battle.getInitiatorUser())))
                .thenAnswer(invocation -> {
                    battle.setStatus(BattleStatus.FINISHED);
                    return battle;
                });

        PveTurnResultView result = pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID);

        assertThat(battle.getOpponentCurrentHp()).isEqualTo(80);
        assertThat(battle.getInitiatorCurrentHp()).isEqualTo(0);
        assertThat(result.playerAttack().damage()).isEqualTo(20);
        assertThat(result.machineAttack().damage()).isEqualTo(27);
        assertThat(result.battle().status()).isEqualTo(BattleStatus.FINISHED);
        verify(battleFinisher).finishWithMachineWinner(battle, battle.getInitiatorUser());
        verify(battleFinisher, never()).finishWithHumanWinnerAgainstMachine(any(), any());
    }

    @Test
    void bothSurviveIncrementsTurnNumberAndReturnsBothAttacks() {
        Battle battle = pveBattle(BattleStatus.IN_PROGRESS, 100, 100);
        when(battleRepository.findById(BATTLE_ID)).thenReturn(Optional.of(battle));
        when(attackRoller.roll(20)).thenReturn(new AttackRoll(AttackLevel.NORMAL, 1.0, 20));
        when(attackRoller.roll(18)).thenReturn(new AttackRoll(AttackLevel.NORMAL, 1.0, 18));

        PveTurnResultView result = pveBattleService.applyTurn(INITIATOR_ID, BATTLE_ID);

        assertThat(battle.getOpponentCurrentHp()).isEqualTo(80);
        assertThat(battle.getInitiatorCurrentHp()).isEqualTo(82);
        assertThat(battle.getTurnNumber()).isEqualTo(2);
        assertThat(result.playerAttack().damage()).isEqualTo(20);
        assertThat(result.machineAttack().damage()).isEqualTo(18);
        assertThat(result.battle().status()).isEqualTo(BattleStatus.IN_PROGRESS);
        verify(battleFinisher, never()).finishWithHumanWinnerAgainstMachine(any(), any());
        verify(battleFinisher, never()).finishWithMachineWinner(any(), any());
    }

    private Battle pvpBattle(int initiatorHp, int opponentHp) {
        User initiator = userWithId(INITIATOR_ID);
        User opponent = userWithId(2L);
        Character initiatorCharacter = characterWithAttack(20);
        Character opponentCharacter = characterWithAttack(18);

        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setOpponentUser(opponent);
        battle.setOpponentCharacter(opponentCharacter);
        battle.setStatus(BattleStatus.IN_PROGRESS);
        battle.setInitiatorCurrentHp(initiatorHp);
        battle.setOpponentCurrentHp(opponentHp);
        return battle;
    }

    private Battle pveBattle(BattleStatus status, int initiatorHp, int machineHp) {
        User initiator = userWithId(INITIATOR_ID);
        Character initiatorCharacter = characterWithAttack(20);
        Character machineCharacter = characterWithAttack(18);

        Battle battle = new Battle(BattleMode.PVE, initiator, initiatorCharacter);
        battle.setOpponentCharacter(machineCharacter);
        battle.setStatus(status);
        battle.setInitiatorCurrentHp(initiatorHp);
        battle.setOpponentCurrentHp(machineHp);
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
