package com.starsbattle.battles.dto;

import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit layer: no Spring context — proves {@link UserBattleSummary} never
 * leaks a battle participant's email to the opponent/admin viewing a battle
 * (spec: {@code GET /battles/:id}), same privacy hardening already applied
 * to {@code RankingRow} (see README "Deliberate deviations from the
 * reference implementation").
 */
class UserBattleSummaryTest {

    @Test
    void userBattleSummaryHasNoEmailField() {
        assertThat(UserBattleSummary.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "level", "xp", "wins", "losses")
                .doesNotContain("email");
    }

    @Test
    void fromNeverIncludesEmailData() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getLevel()).thenReturn(5);
        when(user.getXp()).thenReturn(90);
        when(user.getWins()).thenReturn(3);
        when(user.getLosses()).thenReturn(1);

        UserBattleSummary summary = UserBattleSummary.from(user);

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.level()).isEqualTo(5);
        assertThat(summary.xp()).isEqualTo(90);
        assertThat(summary.wins()).isEqualTo(3);
        assertThat(summary.losses()).isEqualTo(1);
    }
}
