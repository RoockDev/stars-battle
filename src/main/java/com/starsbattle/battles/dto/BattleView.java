package com.starsbattle.battles.dto;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.domain.BattleTurn;

import java.time.Instant;

/**
 * The battle "public view" projection (spec: {@code GET /battles/:id}) —
 * nested initiator/opponent/winner user summaries and initiator/opponent
 * character summaries, never the raw entity (so the password hash can never
 * leak). Opponent/winner fields are {@code null} until they exist (WAITING
 * PVP battle with no opponent yet, or an unfinished battle with no winner).
 */
public record BattleView(
        Long id,
        BattleMode mode,
        BattleStatus status,
        UserBattleSummary initiatorUser,
        UserBattleSummary opponentUser,
        UserBattleSummary winnerUser,
        CharacterBattleSummary initiatorCharacter,
        CharacterBattleSummary opponentCharacter,
        Integer initiatorCurrentHp,
        Integer opponentCurrentHp,
        Integer turnNumber,
        BattleTurn nextTurn,
        Long winnerUserId,
        Boolean winnerIsMachine,
        Instant endedAt,
        Instant createdAt) {

    public static BattleView from(Battle battle) {
        return new BattleView(
                battle.getId(),
                battle.getMode(),
                battle.getStatus(),
                UserBattleSummary.from(battle.getInitiatorUser()),
                battle.getOpponentUser() == null ? null : UserBattleSummary.from(battle.getOpponentUser()),
                battle.getWinnerUser() == null ? null : UserBattleSummary.from(battle.getWinnerUser()),
                CharacterBattleSummary.from(battle.getInitiatorCharacter()),
                battle.getOpponentCharacter() == null ? null : CharacterBattleSummary.from(battle.getOpponentCharacter()),
                battle.getInitiatorCurrentHp(),
                battle.getOpponentCurrentHp(),
                battle.getTurnNumber(),
                battle.getNextTurn(),
                battle.getWinnerUser() == null ? null : battle.getWinnerUser().getId(),
                battle.getWinnerIsMachine(),
                battle.getEndedAt(),
                battle.getCreatedAt());
    }
}
