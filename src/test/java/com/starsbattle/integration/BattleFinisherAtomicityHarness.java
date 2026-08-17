package com.starsbattle.integration;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.service.BattleFinisher;
import com.starsbattle.users.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only transactional wrapper used exclusively by
 * {@code BattleFinisherAtomicityIT}. {@link BattleFinisher}'s methods use
 * {@code Propagation.MANDATORY} (design: BattleFinisher must never open its
 * own transaction) so they can only be exercised from inside an already
 * -active transaction — this harness plays the role the real caller
 * ({@code PvpBattleService}, PR11) will play, opening the transaction and
 * optionally forcing a rollback to prove the battle-close and reward
 * updates commit (or discard) together, never independently.
 */
@Component
public class BattleFinisherAtomicityHarness {

    private final BattleFinisher battleFinisher;

    public BattleFinisherAtomicityHarness(BattleFinisher battleFinisher) {
        this.battleFinisher = battleFinisher;
    }

    @Transactional
    public void finishAndCommit(Battle battle, User winner, User loser) {
        battleFinisher.finishWithHumanWinner(battle, winner, loser);
    }

    @Transactional
    public void finishThenForceRollback(Battle battle, User winner, User loser) {
        battleFinisher.finishWithHumanWinner(battle, winner, loser);
        throw new IllegalStateException("forced rollback to prove atomicity");
    }
}
