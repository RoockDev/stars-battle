package com.starsbattle.battles.domain;

/**
 * Damage-roll tier (spec: "Attack Roll Contract"). Multipliers are exact
 * values byte-verified from the source's {@code rollAttack} function — do
 * not "round" them to nicer numbers.
 */
public enum AttackLevel {

    BAJO(0.8),
    NORMAL(1.0),
    ALTO(1.2),
    CRITICO(1.5);

    private final double multiplier;

    AttackLevel(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }
}
