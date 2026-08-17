package com.starsbattle.users.dto;

/** One position in the ranking (spec: "Ranking Query"), rank is 1-based. */
public record RankingRow(int rank, Long id, String email, Integer wins, Integer losses, Integer xp, Integer level) {
}
