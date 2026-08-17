package com.starsbattle.users.service;

import com.starsbattle.users.domain.User;
import com.starsbattle.users.dto.RankingRow;
import com.starsbattle.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repository, no Spring context — fast feedback on the
 * ranking limit clamp (spec: "Ranking Query" scenario "Default and
 * clamping") and rank assignment. Ordering itself is proven at the
 * persistence layer by {@code UserRepositoryTest}.
 */
@ExtendWith(MockitoExtension.class)
class UserRankingServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserRankingService userRankingService;

    @BeforeEach
    void setUp() {
        userRankingService = new UserRankingService(userRepository);
    }

    @Test
    void nullLimitDefaultsToTen() {
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        userRankingService.getRanking(null);

        assertEffectiveLimit(10);
    }

    @Test
    void limitOfZeroClampsToOne() {
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        userRankingService.getRanking(0);

        assertEffectiveLimit(1);
    }

    @Test
    void negativeLimitClampsToOne() {
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        userRankingService.getRanking(-5);

        assertEffectiveLimit(1);
    }

    @Test
    void limitAboveOneHundredClampsToOneHundred() {
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        userRankingService.getRanking(500);

        assertEffectiveLimit(100);
    }

    @Test
    void limitWithinRangeIsUsedAsIs() {
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        userRankingService.getRanking(25);

        assertEffectiveLimit(25);
    }

    @Test
    void mapsRepositoryOrderToOneBasedRankRows() {
        User first = userWith(1L, "most-wins@batalla.com", 10, 1, 90, 3);
        User second = userWith(2L, "second@batalla.com", 5, 2, 40, 2);
        when(userRepository.findAllByOrderByWinsDescLossesAscXpDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(first, second));

        List<RankingRow> ranking = userRankingService.getRanking(10);

        assertThat(ranking).hasSize(2);
        RankingRow row1 = ranking.get(0);
        assertThat(row1.rank()).isEqualTo(1);
        assertThat(row1.id()).isEqualTo(1L);
        assertThat(row1.email()).isEqualTo("most-wins@batalla.com");
        assertThat(row1.wins()).isEqualTo(10);
        assertThat(row1.losses()).isEqualTo(1);
        assertThat(row1.xp()).isEqualTo(90);
        assertThat(row1.level()).isEqualTo(3);

        RankingRow row2 = ranking.get(1);
        assertThat(row2.rank()).isEqualTo(2);
        assertThat(row2.id()).isEqualTo(2L);
    }

    private void assertEffectiveLimit(int expectedLimit) {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllByOrderByWinsDescLossesAscXpDesc(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, expectedLimit));
    }

    private User userWith(Long id, String email, int wins, int losses, int xp, int level) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.getWins()).thenReturn(wins);
        when(user.getLosses()).thenReturn(losses);
        when(user.getXp()).thenReturn(xp);
        when(user.getLevel()).thenReturn(level);
        return user;
    }
}
