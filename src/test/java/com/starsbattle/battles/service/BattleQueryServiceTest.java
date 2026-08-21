package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repository + real {@link BattleAccessChecker} (pure,
 * cheap to construct directly) — spec: "GET /battles/:id" 404/403 branches.
 */
@ExtendWith(MockitoExtension.class)
class BattleQueryServiceTest {

    @Mock
    private BattleRepository battleRepository;

    private BattleQueryService battleQueryService;

    @BeforeEach
    void setUp() {
        battleQueryService = new BattleQueryService(battleRepository, new BattleAccessChecker());
    }

    @Test
    void returnsBattleViewWhenBattleExistsAndCallerIsInitiator() {
        Battle battle = battleWithInitiator(1L);
        when(battleRepository.findWithAssociationsById(42L)).thenReturn(Optional.of(battle));

        BattleView view = battleQueryService.getBattleView(42L, 1L, false);

        assertThat(view.mode()).isEqualTo(BattleMode.PVP);
    }

    @Test
    void returnedBattleViewNeverIncludesParticipantEmail() {
        Battle battle = battleWithInitiator(1L);
        when(battleRepository.findWithAssociationsById(42L)).thenReturn(Optional.of(battle));

        BattleView view = battleQueryService.getBattleView(42L, 1L, false);

        assertThat(view.initiatorUser().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("email");
    }

    @Test
    void adminCanViewAnyBattle() {
        Battle battle = battleWithInitiator(1L);
        when(battleRepository.findWithAssociationsById(42L)).thenReturn(Optional.of(battle));

        BattleView view = battleQueryService.getBattleView(42L, 999L, true);

        assertThat(view).isNotNull();
    }

    @Test
    void nonParticipantIsRejectedWithForbidden() {
        User initiator = mock(User.class);
        when(initiator.getId()).thenReturn(1L);
        Character character = new Character("Luke Skywalker", 100, 100, 20, 1);
        Battle battle = new Battle(BattleMode.PVP, initiator, character);
        when(battleRepository.findWithAssociationsById(42L)).thenReturn(Optional.of(battle));

        assertThatThrownBy(() -> battleQueryService.getBattleView(42L, 2L, false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingBattleThrowsNotFound() {
        when(battleRepository.findWithAssociationsById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> battleQueryService.getBattleView(42L, 1L, false))
                .isInstanceOf(NotFoundException.class);
    }

    private Battle battleWithInitiator(Long initiatorId) {
        User initiator = mock(User.class);
        when(initiator.getId()).thenReturn(initiatorId);
        when(initiator.getLevel()).thenReturn(1);
        when(initiator.getXp()).thenReturn(0);
        when(initiator.getWins()).thenReturn(0);
        when(initiator.getLosses()).thenReturn(0);

        Character character = new Character("Luke Skywalker", 100, 100, 20, 1);

        return new Battle(BattleMode.PVP, initiator, character);
    }
}
