package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.domain.RewardCalculator;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Shared "close battle + apply rewards" boundary (design: "Data Flow — turn
 * request", spec: "Win/Loss Reward Contract"). Both {@code PvpBattleService}
 * (PR11) and {@code PveBattleService} (PR12) call this on a knockout so
 * battle-close and reward application can never run in separate
 * transactions — the equivalent of the source's Prisma {@code $transaction}
 * boundary. Three shapes cover the three winner combinations: two humans
 * ({@link #finishWithHumanWinner}), a human beating the machine
 * ({@link #finishWithHumanWinnerAgainstMachine} — win reward only, there is
 * no loser {@code User} to penalize), and the machine beating a human
 * ({@link #finishWithMachineWinner} — loss reward only, there is no winner
 * {@code User} to reward).
 *
 * <p>{@code Propagation.MANDATORY} is deliberate (design's "two load
 * -bearing consequences"): this method must always run inside a transaction
 * already opened by its caller (the turn-resolution service), never open
 * its own — that would let the battle-close and reward updates commit
 * independently of the caller's own writes (e.g. the attacker's HP change),
 * defeating the atomicity guarantee this class exists for.
 *
 * <p>No WebSocket broadcast happens here yet — {@link #publishBattleFinished}
 * is the extension point PR13 will use to publish a
 * {@code BattleChangedEvent} for a {@code @TransactionalEventListener(
 * AFTER_COMMIT)} listener to broadcast, without this transaction boundary
 * needing to change.
 */
@Component
public class BattleFinisher {

    private final UserRepository userRepository;
    private final BattleRepository battleRepository;

    public BattleFinisher(UserRepository userRepository, BattleRepository battleRepository) {
        this.userRepository = userRepository;
        this.battleRepository = battleRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Battle finishWithHumanWinner(Battle battle, User winner, User loser) {
        applyWinReward(winner);
        applyLossReward(loser);
        userRepository.save(winner);
        userRepository.save(loser);

        closeBattle(battle, winner);
        publishBattleFinished(battle);
        return battle;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Battle finishWithHumanWinnerAgainstMachine(Battle battle, User winner) {
        applyWinReward(winner);
        userRepository.save(winner);

        closeBattle(battle, winner);
        publishBattleFinished(battle);
        return battle;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Battle finishWithMachineWinner(Battle battle, User humanLoser) {
        applyLossReward(humanLoser);
        userRepository.save(humanLoser);

        closeBattle(battle, null);
        publishBattleFinished(battle);
        return battle;
    }

    private void applyWinReward(User winner) {
        RewardCalculator.WinReward reward = RewardCalculator.applyWin(winner.getXp(), winner.getWins());
        winner.setXp(reward.xp());
        winner.setLevel(reward.level());
        winner.setWins(reward.wins());
    }

    private void applyLossReward(User loser) {
        RewardCalculator.LossReward reward = RewardCalculator.applyLoss(loser.getLosses());
        loser.setLosses(reward.losses());
    }

    private void closeBattle(Battle battle, User winner) {
        battle.setStatus(BattleStatus.FINISHED);
        battle.setEndedAt(Instant.now());
        battle.setWinnerUser(winner);
        // winnerIsMachine is derived from winner nullity rather than taken as
        // a separate caller-supplied flag — a User winner and a machine
        // winner are mutually exclusive by construction, so an independent
        // boolean parameter would allow an inconsistent (winner, isMachine)
        // pair that nothing here prevents.
        battle.setWinnerIsMachine(winner == null);
        // Explicit save (symmetric with the winner/loser saves above) rather
        // than relying purely on dirty-checking: it also correctly merges
        // the change back if the caller handed us a battle instance loaded
        // outside the current persistence context.
        battleRepository.save(battle);
    }

    private void publishBattleFinished(Battle battle) {
        // No-op for now (PR10 scope is only the transactional close+reward
        // boundary). PR13 wires an ApplicationEventPublisher here to publish
        // a BattleChangedEvent, consumed by a
        // @TransactionalEventListener(phase = AFTER_COMMIT) broadcaster —
        // that listener never fires for a rolled-back or lock-failed turn.
    }
}
