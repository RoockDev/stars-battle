package com.starsbattle.battles.domain;

/**
 * The outcome of a single attack roll (spec: "Attack Roll Contract").
 * {@code rolledAttack} equals the damage dealt — there is no separate
 * defense stat, and attacks never miss ({@code rolledAttack >= 1} always).
 */
public record AttackRoll(AttackLevel level, double multiplier, int rolledAttack) {
}
