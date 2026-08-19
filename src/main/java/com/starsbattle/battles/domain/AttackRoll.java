package com.starsbattle.battles.domain;

/**
 * The outcome of a single attack roll (spec: "Attack Roll Contract").
 * {@code rolledAttack} equals the damage dealt — there is no separate
 * defense stat, and attacks never miss ({@code rolledAttack >= 1} always).
 */
public record AttackRoll(AttackLevel level, int rolledAttack) {

    /**
     * Derived from {@link #level} rather than stored — a stored copy would
     * be a second source of truth with nothing enforcing it stays in sync
     * with {@code level}.
     */
    public double multiplier() {
        return level.multiplier();
    }
}
