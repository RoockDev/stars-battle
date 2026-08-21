package com.starsbattle.battles.dto;

import com.starsbattle.users.domain.User;

/**
 * Nested user projection used inside {@link BattleView} — never includes the
 * password hash or email (see README "Deliberate deviations from the
 * reference implementation": a battle opponent/admin has no legitimate need
 * to see a peer's contact info, the same reasoning already applied to
 * {@code RankingRow}), and deliberately narrower than
 * {@code auth.dto.UserSummary} (no roles field; battle views don't need it).
 */
public record UserBattleSummary(Long id, Integer level, Integer xp, Integer wins, Integer losses) {

    public static UserBattleSummary from(User user) {
        return new UserBattleSummary(
                user.getId(), user.getLevel(), user.getXp(), user.getWins(), user.getLosses());
    }
}
