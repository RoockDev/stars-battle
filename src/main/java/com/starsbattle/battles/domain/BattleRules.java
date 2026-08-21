package com.starsbattle.battles.domain;

import com.starsbattle.common.exception.BusinessRuleException;

import java.util.Objects;

/**
 * Pure battle guard rules for creation and PVP join (spec: "Start PVE
 * Battle", "Start PVP Battle", "Join PVP Battle"). Framework-free — throws
 * the project's own lightweight {@link BusinessRuleException} (itself zero
 * -Spring), never a Spring exception type, so this class stays exercisable
 * with plain JUnit and no Spring context. Turn-resolution guards are added
 * alongside {@code PvpBattleService} (PR11).
 */
public final class BattleRules {

    private BattleRules() {
        // static utility
    }

    public static void assertDistinctCharacters(Long characterIdA, Long characterIdB, String message) {
        if (Objects.equals(characterIdA, characterIdB)) {
            throw new BusinessRuleException(message);
        }
    }

    public static void assertLevelGate(int userLevel, int requiredLevel) {
        if (userLevel < requiredLevel) {
            throw new BusinessRuleException(
                    "Se requiere nivel " + requiredLevel + " para usar este personaje (nivel actual: "
                            + userLevel + ")");
        }
    }

    public static void assertMode(BattleMode actual, BattleMode expected, String message) {
        if (actual != expected) {
            throw new BusinessRuleException(message);
        }
    }

    public static void assertStatus(BattleStatus actual, BattleStatus expected, String message) {
        if (actual != expected) {
            throw new BusinessRuleException(message);
        }
    }

    public static void assertNoOpponentYet(boolean opponentAlreadyPresent, String message) {
        if (opponentAlreadyPresent) {
            throw new BusinessRuleException(message);
        }
    }

    public static void assertActorIsNotInitiator(Long actorUserId, Long initiatorUserId, String message) {
        if (Objects.equals(actorUserId, initiatorUserId)) {
            throw new BusinessRuleException(message);
        }
    }

    /**
     * PVP turn-resolution status guard (spec: "PVP Turn Resolution" scenario
     * "Wrong status") — FINISHED and WAITING are both invalid for taking a
     * turn, but get distinct messages, unlike the generic
     * {@link #assertStatus}.
     */
    public static void assertInProgressForTurn(BattleStatus status) {
        if (status == BattleStatus.FINISHED) {
            throw new BusinessRuleException("La batalla ya ha finalizado");
        }
        if (status == BattleStatus.WAITING) {
            throw new BusinessRuleException("La batalla no esta en progreso");
        }
    }
}
