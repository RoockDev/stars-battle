package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.domain.BattleTurn;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.JoinPvpRequest;
import com.starsbattle.battles.dto.StartPveRequest;
import com.starsbattle.battles.dto.StartPvpRequest;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repositories + a real {@link BattleParticipantValidator}
 * (cheap to construct with mocked collaborators) — spec: "Start PVE Battle",
 * "Start PVP Battle", "Join PVP Battle".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BattleCreationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private BattleRepository battleRepository;

    private BattleCreationService battleCreationService;

    @BeforeEach
    void setUp() {
        BattleParticipantValidator validator = new BattleParticipantValidator(userRepository, characterRepository);
        battleCreationService = new BattleCreationService(validator, characterRepository, battleRepository);
        when(battleRepository.save(any(Battle.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---- startPve ----

    @Test
    void startPveCreatesInProgressBattleWithBothHpSet() {
        User user = userWithLevel(1L, 5);
        Character myCharacter = characterWith(10L, 100, 1);
        Character machineCharacter = characterWith(20L, 80, 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(myCharacter));
        when(characterRepository.findById(20L)).thenReturn(Optional.of(machineCharacter));

        BattleView view = battleCreationService.startPve(1L, new StartPveRequest(10L, 20L));

        assertThat(view.mode()).isEqualTo(BattleMode.PVE);
        assertThat(view.status()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThat(view.initiatorCurrentHp()).isEqualTo(100);
        assertThat(view.opponentCurrentHp()).isEqualTo(80);
        assertThat(view.turnNumber()).isEqualTo(1);
        assertThat(view.nextTurn()).isEqualTo(BattleTurn.INITIATOR);
    }

    @Test
    void startPveRejectsEqualCharacterIds() {
        assertThatThrownBy(() -> battleCreationService.startPve(1L, new StartPveRequest(10L, 10L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void startPveRejectsWhenUserLevelBelowRequirement() {
        User user = userWithLevel(1L, 1);
        Character myCharacter = characterWith(10L, 100, 5);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(myCharacter));

        assertThatThrownBy(() -> battleCreationService.startPve(1L, new StartPveRequest(10L, 20L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void startPveThrowsNotFoundWhenMachineCharacterMissing() {
        User user = userWithLevel(1L, 5);
        Character myCharacter = characterWith(10L, 100, 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(myCharacter));
        when(characterRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleCreationService.startPve(1L, new StartPveRequest(10L, 20L)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- startPvp ----

    @Test
    void startPvpCreatesWaitingBattleWithNoOpponent() {
        User user = userWithLevel(1L, 5);
        Character myCharacter = characterWith(10L, 100, 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(myCharacter));

        BattleView view = battleCreationService.startPvp(1L, new StartPvpRequest(10L));

        assertThat(view.mode()).isEqualTo(BattleMode.PVP);
        assertThat(view.status()).isEqualTo(BattleStatus.WAITING);
        assertThat(view.opponentUser()).isNull();
        assertThat(view.opponentCharacter()).isNull();
        assertThat(view.initiatorCurrentHp()).isEqualTo(100);
        assertThat(view.opponentCurrentHp()).isEqualTo(0);
    }

    @Test
    void startPvpRejectsWhenUserLevelBelowRequirement() {
        User user = userWithLevel(1L, 1);
        Character myCharacter = characterWith(10L, 100, 5);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(myCharacter));

        assertThatThrownBy(() -> battleCreationService.startPvp(1L, new StartPvpRequest(10L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- joinPvp ----

    @Test
    void joinPvpSetsOpponentAndTransitionsToInProgress() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.WAITING);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        User opponent = userWithLevel(2L, 5);
        Character opponentCharacter = characterWith(11L, 90, 1);
        when(userRepository.findById(2L)).thenReturn(Optional.of(opponent));
        when(characterRepository.findById(11L)).thenReturn(Optional.of(opponentCharacter));

        BattleView view = battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L));

        assertThat(view.status()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThat(view.opponentUser()).isNotNull();
        assertThat(view.opponentCurrentHp()).isEqualTo(90);
    }

    @Test
    void joinPvpThrowsNotFoundWhenBattleMissing() {
        when(battleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void joinPvpRejectsNonPvpMode() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVE, initiator, initiatorCharacter);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void joinPvpRejectsWhenBattleNotWaiting() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.IN_PROGRESS);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void joinPvpRejectsInitiatorJoiningOwnBattle() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.WAITING);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleCreationService.joinPvp(1L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void joinPvpRejectsWhenOpponentAlreadyExists() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.WAITING);
        battle.setOpponentUser(userWithLevel(3L, 5));
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void joinPvpRejectsMirrorMatchCharacter() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.WAITING);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(10L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void joinPvpRejectsWhenJoiningUserLevelBelowRequirement() {
        User initiator = userWithLevel(1L, 5);
        Character initiatorCharacter = characterWith(10L, 100, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setStatus(BattleStatus.WAITING);
        when(battleRepository.findById(99L)).thenReturn(Optional.of(battle));

        User opponent = userWithLevel(2L, 1);
        Character opponentCharacter = characterWith(11L, 90, 5);
        when(userRepository.findById(2L)).thenReturn(Optional.of(opponent));
        when(characterRepository.findById(11L)).thenReturn(Optional.of(opponentCharacter));

        assertThatThrownBy(() -> battleCreationService.joinPvp(2L, 99L, new JoinPvpRequest(11L)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private User userWithLevel(Long id, int level) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getLevel()).thenReturn(level);
        return user;
    }

    private Character characterWith(Long id, int hp, int levelRequired) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getHp()).thenReturn(hp);
        when(character.getLevelRequired()).thenReturn(levelRequired);
        return character;
    }
}
