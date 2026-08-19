package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /battles/:id} — spec: "GET /battles/:id" authorization. */
@Service
public class BattleQueryService {

    private static final String BATTLE_NOT_FOUND_MESSAGE = "Batalla no encontrada";

    private final BattleRepository battleRepository;
    private final BattleAccessChecker battleAccessChecker;

    public BattleQueryService(BattleRepository battleRepository, BattleAccessChecker battleAccessChecker) {
        this.battleRepository = battleRepository;
        this.battleAccessChecker = battleAccessChecker;
    }

    @Transactional(readOnly = true)
    public BattleView getBattleView(Long battleId, Long callerUserId, boolean callerIsAdmin) {
        Battle battle = battleRepository.findWithAssociationsById(battleId)
                .orElseThrow(() -> new NotFoundException(BATTLE_NOT_FOUND_MESSAGE));
        battleAccessChecker.assertCanView(battle, callerUserId, callerIsAdmin);
        return BattleView.from(battle);
    }
}
