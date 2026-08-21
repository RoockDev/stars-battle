package com.starsbattle.battles.domain;

/**
 * The exact damage-roll algorithm (spec: "Attack Roll Contract"), byte
 * -verified from the source's {@code rollAttack(baseAttack)}: draw a
 * uniform {@code r} in {@code [0,1)}; {@code r<0.20} -&gt; BAJO (x0.8),
 * {@code r<0.75} -&gt; NORMAL (x1.0), {@code r<0.95} -&gt; ALTO (x1.2), else
 * -&gt; CRITICO (x1.5). {@code rolledAttack = max(1, round(baseAttack *
 * multiplier))} — attacks never miss. Uses {@link Math#round}, not
 * {@code BigDecimal.HALF_EVEN}, to stay identical to JS {@code Math.round}
 * for this non-negative domain (design: "Core Interfaces" parity gotchas).
 */
public final class RandomAttackRoller implements AttackRoller {

    private final RandomSource randomSource;

    public RandomAttackRoller(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    @Override
    public AttackRoll roll(int baseAttack) {
        double r = randomSource.nextDouble();
        AttackLevel level = r < 0.20 ? AttackLevel.BAJO
                : r < 0.75 ? AttackLevel.NORMAL
                : r < 0.95 ? AttackLevel.ALTO
                : AttackLevel.CRITICO;

        long roundedAttack = Math.round(baseAttack * level.multiplier());
        int rolled = (int) Math.max(1L, Math.min(roundedAttack, Integer.MAX_VALUE));
        return new AttackRoll(level, rolled);
    }
}
