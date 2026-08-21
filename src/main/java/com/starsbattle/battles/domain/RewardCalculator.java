package com.starsbattle.battles.domain;

/**
 * The exact win/loss reward formulas (spec: "Win/Loss Reward Contract"),
 * byte-verified from the source. On win: {@code xp += 10}, {@code level =
 * floor(newXp / 100) + 1} (always recomputed from the new xp total, never
 * incremented), {@code wins += 1}. On loss: {@code losses += 1} only, xp
 * and level unchanged. Pure, static, framework-free — consumed later by
 * {@code BattleFinisher} (PR9/PR10) so the battle-close transaction can
 * apply rewards without touching the database from this class.
 */
public final class RewardCalculator {

    private RewardCalculator() {
        // static utility
    }

    public static int xpAfterWin(int currentXp) {
        return currentXp + 10;
    }

    public static int levelFor(int xp) {
        return Math.floorDiv(xp, 100) + 1;
    }

    public static WinReward applyWin(int currentXp, int currentWins) {
        int newXp = xpAfterWin(currentXp);
        return new WinReward(newXp, levelFor(newXp), currentWins + 1);
    }

    public static LossReward applyLoss(int currentLosses) {
        return new LossReward(currentLosses + 1);
    }

    public record WinReward(int xp, int level, int wins) {
    }

    public record LossReward(int losses) {
    }
}
