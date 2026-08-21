package com.starsbattle.battles.dto;

import com.starsbattle.battles.domain.AttackLevel;

/**
 * PVE turn response (spec: "PVE Turn Resolution") — up to two attacks
 * resolve within one call: the player's attack always happens
 * ({@link #playerAttack()}), the machine's counter-attack only happens if
 * the player did not knock the machine out this turn
 * ({@link #machineAttack()} is {@code null} otherwise, per the "Player wins
 * immediately" scenario — the machine never gets to counter that turn).
 */
public record PveTurnResultView(
        PveAttackView playerAttack,
        PveAttackView machineAttack,
        BattleView battle) {

    public record PveAttackView(
            AttackLevel attackLevel,
            Integer baseAttack,
            Integer rolledAttack,
            Integer damage) {
    }
}
