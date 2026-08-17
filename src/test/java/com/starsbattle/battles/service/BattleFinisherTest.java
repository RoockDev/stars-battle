package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.event.BattleUpdatedEvent;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit layer: mocked {@link UserRepository}, no Spring context — spec:
 * "Win/Loss Reward Contract" applied atomically alongside the battle-close
 * update (design: "Data Flow — turn request", BattleFinisher). The
 * transactional-atomicity claim itself (both changes commit together, or
 * neither does) is proven separately by
 * {@code BattleFinisherAtomicityIT} — a mocked-repository unit test cannot
 * exercise a real transaction boundary.
 */
@ExtendWith(MockitoExtension.class)
class BattleFinisherTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BattleRepository battleRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BattleFinisher battleFinisher;

    @BeforeEach
    void setUp() {
        battleFinisher = new BattleFinisher(userRepository, battleRepository, eventPublisher);
    }

    @Test
    void finishWithHumanWinnerAppliesWinAndLossRewardsAndClosesBattle() {
        Battle battle = battleInProgress(BattleMode.PVP);
        User winner = userWith(90, 0, 3);
        User loser = userWith(50, 2, 1);

        battleFinisher.finishWithHumanWinner(battle, winner, loser);

        assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(battle.getEndedAt()).isNotNull();
        assertThat(battle.getWinnerUser()).isEqualTo(winner);
        assertThat(battle.getWinnerIsMachine()).isFalse();

        assertThat(winner.getXp()).isEqualTo(100);
        assertThat(winner.getLevel()).isEqualTo(2);
        assertThat(winner.getWins()).isEqualTo(4);

        assertThat(loser.getLosses()).isEqualTo(3);
        assertThat(loser.getXp()).isEqualTo(50);

        verify(userRepository).save(winner);
        verify(userRepository).save(loser);
        verify(battleRepository).save(battle);
    }

    @Test
    void finishWithMachineWinnerAppliesOnlyLossRewardToInitiator() {
        Battle battle = battleInProgress(BattleMode.PVE);
        User humanLoser = userWith(20, 4, 1);

        battleFinisher.finishWithMachineWinner(battle, humanLoser);

        assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(battle.getEndedAt()).isNotNull();
        assertThat(battle.getWinnerUser()).isNull();
        assertThat(battle.getWinnerIsMachine()).isTrue();

        assertThat(humanLoser.getLosses()).isEqualTo(5);
        assertThat(humanLoser.getXp()).isEqualTo(20);

        verify(userRepository).save(humanLoser);
        verify(battleRepository).save(battle);
    }

    @Test
    void closeBattleDerivesWinnerIsMachineFromWinnerNullityRatherThanACallerFlag() {
        Battle humanWinnerBattle = battleInProgress(BattleMode.PVP);
        User winner = userWith(0, 0, 0);
        User loser = userWith(0, 0, 0);
        battleFinisher.finishWithHumanWinner(humanWinnerBattle, winner, loser);
        assertThat(humanWinnerBattle.getWinnerIsMachine())
                .as("a non-null winner must derive winnerIsMachine=false")
                .isFalse();

        Battle machineWinnerBattle = battleInProgress(BattleMode.PVE);
        User humanLoser = userWith(0, 0, 0);
        battleFinisher.finishWithMachineWinner(machineWinnerBattle, humanLoser);
        assertThat(machineWinnerBattle.getWinnerIsMachine())
                .as("a null winner must derive winnerIsMachine=true")
                .isTrue();
    }

    @Test
    void finishWithHumanWinnerAgainstMachineAppliesOnlyWinRewardToInitiator() {
        Battle battle = battleInProgress(BattleMode.PVE);
        User winner = userWith(90, 0, 3);

        battleFinisher.finishWithHumanWinnerAgainstMachine(battle, winner);

        assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(battle.getEndedAt()).isNotNull();
        assertThat(battle.getWinnerUser()).isEqualTo(winner);
        assertThat(battle.getWinnerIsMachine()).isFalse();

        assertThat(winner.getXp()).isEqualTo(100);
        assertThat(winner.getLevel()).isEqualTo(2);
        assertThat(winner.getWins()).isEqualTo(4);

        verify(userRepository).save(winner);
        verify(userRepository, times(1)).save(any(User.class));
        verify(battleRepository).save(battle);
    }

    @Test
    void finishPublishesBattleFinishedEventWithBattleIdAndView() {
        Battle battle = battleInProgress(BattleMode.PVP);
        User winner = userWith(90, 0, 3);
        User loser = userWith(50, 2, 1);

        battleFinisher.finishWithHumanWinner(battle, winner, loser);

        ArgumentCaptor<BattleUpdatedEvent> captor = ArgumentCaptor.forClass(BattleUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BattleUpdatedEvent event = captor.getValue();
        assertThat(event.battleId()).isEqualTo(battle.getId());
        assertThat(event.type()).isEqualTo(BattleUpdatedEvent.Type.BATTLE_FINISHED);
        assertThat(event.payload()).isNotNull();
    }

    @Test
    void finishMethodsRequireAnAlreadyActiveTransaction() throws NoSuchMethodException {
        Method finishWithHumanWinner = BattleFinisher.class.getMethod(
                "finishWithHumanWinner", Battle.class, User.class, User.class);
        Method finishWithMachineWinner = BattleFinisher.class.getMethod(
                "finishWithMachineWinner", Battle.class, User.class);
        Method finishWithHumanWinnerAgainstMachine = BattleFinisher.class.getMethod(
                "finishWithHumanWinnerAgainstMachine", Battle.class, User.class);

        assertThat(finishWithHumanWinner.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(finishWithMachineWinner.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(finishWithHumanWinnerAgainstMachine.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }

    private Battle battleInProgress(BattleMode mode) {
        User initiator = new User("initiator@batalla.com", "hash");
        Character character = new Character("Luke Skywalker", 100, 100, 20, 1);
        Battle battle = new Battle(mode, initiator, character);
        battle.setStatus(BattleStatus.IN_PROGRESS);
        return battle;
    }

    private User userWith(int xp, int losses, int wins) {
        User user = new User("participant@batalla.com", "hash");
        user.setXp(xp);
        user.setLosses(losses);
        user.setWins(wins);
        return user;
    }
}
