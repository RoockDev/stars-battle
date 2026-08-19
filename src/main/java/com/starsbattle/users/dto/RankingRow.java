package com.starsbattle.users.dto;

/**
 * One position in the ranking (spec: "Ranking Query"), rank is 1-based.
 *
 * <p>Deliberately omits {@code email} — see README "Deliberate deviations
 * from the reference implementation": a public leaderboard has no
 * legitimate need to expose peer contact info to every authenticated user.
 */
public record RankingRow(int rank, Long id, Integer wins, Integer losses, Integer xp, Integer level) {
}
