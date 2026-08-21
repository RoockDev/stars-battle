package com.starsbattle.battles.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit layer, zero Spring context (design: "Battle domain core — pure
 * Java, no Spring", spec: "Attack Roll Contract"). This is the single
 * highest-value test file in the port — it locks the exact tier boundaries
 * of the damage-roll algorithm byte-verified from the source
 * (r&lt;0.20=BAJO, r&lt;0.75=NORMAL, r&lt;0.95=ALTO, else=CRITICO) so a future
 * refactor can never silently drift the business rule.
 */
class RandomAttackRollerTest {

    private static final int BASE_ATTACK = 20;

    @ParameterizedTest(name = "r={0} -> {1} (x{2})")
    @CsvSource({
            "0.0,     BAJO,     0.8",
            "0.19,    BAJO,     0.8",
            "0.199999,BAJO,     0.8",
            "0.20,    NORMAL,   1.0",
            "0.5,     NORMAL,   1.0",
            "0.74999, NORMAL,   1.0",
            "0.75,    ALTO,     1.2",
            "0.9,     ALTO,     1.2",
            "0.94999, ALTO,     1.2",
            "0.95,    CRITICO,  1.5",
            "0.99,    CRITICO,  1.5",
            "0.999999,CRITICO,  1.5",
    })
    void resolvesExactTierBoundaries(double roll, AttackLevel expectedLevel, double expectedMultiplier) {
        AttackRoller roller = new RandomAttackRoller(fixed(roll));

        AttackRoll result = roller.roll(BASE_ATTACK);

        assertThat(result.level()).isEqualTo(expectedLevel);
        assertThat(result.multiplier()).isEqualTo(expectedMultiplier);
    }

    @Test
    void rolledAttackIsMaxOfOneAndRoundedBaseAttackTimesMultiplier() {
        AttackRoller roller = new RandomAttackRoller(fixed(0.95));

        AttackRoll result = roller.roll(20);

        assertThat(result.rolledAttack()).isEqualTo(30); // round(20 * 1.5) = 30
    }

    @Test
    void attacksNeverMissEvenWhenRoundedDamageWouldBeZeroOrLess() {
        AttackRoller roller = new RandomAttackRoller(fixed(0.0)); // BAJO, x0.8

        AttackRoll result = roller.roll(0);

        assertThat(result.rolledAttack()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void attacksNeverMissAcrossTheFullRollRangeForATypicalAttackStat() {
        for (double r = 0.0; r < 1.0; r += 0.001) {
            AttackRoller roller = new RandomAttackRoller(fixed(r));
            AttackRoll result = roller.roll(20);
            assertThat(result.rolledAttack()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void rolledAttackStaysPositiveEvenWhenTheMultipliedResultOverflowsInt() {
        // baseAttack * CRITICO's x1.5 multiplier = 3_221_225_470.5, which
        // rounds to a long (3_221_225_471) above Integer.MAX_VALUE. The old
        // `(int) Math.max(1L, Math.round(...))` narrowed that long straight
        // to int, wrapping it to -1_073_741_825 — a negative "attack" that
        // breaks the "attacks never miss" invariant. (Integer.MAX_VALUE / 2
        // does NOT reproduce this: 1_073_741_823 * 1.5 = 1_610_612_734.5
        // stays comfortably inside int range, so it wouldn't have caught the
        // bug — this test needs baseAttack close to Integer.MAX_VALUE itself.)
        AttackRoller roller = new RandomAttackRoller(fixed(0.99)); // CRITICO, x1.5

        AttackRoll result = roller.roll(Integer.MAX_VALUE);

        assertThat(result.rolledAttack()).isGreaterThanOrEqualTo(1);
        assertThat(result.rolledAttack()).isEqualTo(Integer.MAX_VALUE);
    }

    private RandomSource fixed(double value) {
        return () -> value;
    }
}
