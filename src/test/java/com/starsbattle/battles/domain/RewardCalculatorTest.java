package com.starsbattle.battles.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit layer, zero Spring context (spec: "Win/Loss Reward Contract").
 * {@code levelFor} is always recomputed from the total xp, never
 * incremented — the boundary cases (99/100/199/200) exist to lock that in.
 */
class RewardCalculatorTest {

    @Test
    void xpAfterWinAddsTenToCurrentXp() {
        assertThat(RewardCalculator.xpAfterWin(95)).isEqualTo(105);
    }

    @ParameterizedTest(name = "xp={0} -> level={1}")
    @CsvSource({
            "0,   1",
            "99,  1",
            "100, 2",
            "199, 2",
            "200, 3",
            "105, 2",
    })
    void levelForIsAlwaysRecomputedFromTotalXp(int xp, int expectedLevel) {
        assertThat(RewardCalculator.levelFor(xp)).isEqualTo(expectedLevel);
    }

    @Test
    void applyWinIncrementsXpRecomputesLevelAndIncrementsWins() {
        RewardCalculator.WinReward reward = RewardCalculator.applyWin(95, 3);

        assertThat(reward.xp()).isEqualTo(105);
        assertThat(reward.level()).isEqualTo(2);
        assertThat(reward.wins()).isEqualTo(4);
    }

    @Test
    void applyLossOnlyIncrementsLosses() {
        RewardCalculator.LossReward reward = RewardCalculator.applyLoss(2);

        assertThat(reward.losses()).isEqualTo(3);
    }
}
