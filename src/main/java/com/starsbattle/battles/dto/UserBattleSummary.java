package com.starsbattle.battles.dto;

import com.starsbattle.users.domain.User;

/**
 * Nested user projection used inside {@link BattleView} — never includes the
 * password hash, and deliberately narrower than {@code auth.dto.UserSummary}
 * (no roles field; battle views don't need it).
 */
public record UserBattleSummary(Long id, String email, Integer level, Integer xp, Integer wins, Integer losses) {

    public static UserBattleSummary from(User user) {
        return new UserBattleSummary(
                user.getId(), user.getEmail(), user.getLevel(), user.getXp(), user.getWins(), user.getLosses());
    }
}
