package com.starsbattle.battles.domain;

/**
 * Resolves a single attack roll for a given base attack stat (spec:
 * "Attack Roll Contract"). Pure, framework-free — consumed later by the
 * PVP/PVE turn-resolution services (PR9-PR12).
 */
public interface AttackRoller {

    AttackRoll roll(int baseAttack);
}
