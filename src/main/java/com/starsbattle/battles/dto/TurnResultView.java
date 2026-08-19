package com.starsbattle.battles.dto;

import com.starsbattle.battles.domain.AttackLevel;
import com.starsbattle.battles.domain.BattleTurn;

/**
 * PVP turn response (spec: "PVP Turn Resolution") — used both for a
 * continuing turn (status stays IN_PROGRESS in the nested {@link #battle()})
 * and for a knockout (status FINISHED, winner fields set): the same shape
 * covers both cases since {@link BattleView} already carries the full
 * post-turn battle state.
 */
public record TurnResultView(
        BattleTurn attackerSide,
        AttackLevel attackLevel,
        Integer baseAttack,
        // rolledAttack and damage are always equal today (AttackRoll has no
        // separate defense stat) and both come from the same
        // roll.rolledAttack() call site in PvpBattleService — kept as two
        // independent fields on this client-facing DTO so a future
        // defense-stat feature can make them diverge without an API change.
        Integer rolledAttack,
        Integer damage,
        BattleView battle) {
}
