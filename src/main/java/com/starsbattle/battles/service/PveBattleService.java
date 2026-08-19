package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.AttackRoll;
import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleRules;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.PveTurnResultView;
import com.starsbattle.battles.dto.PveTurnResultView.PveAttackView;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PVE turn resolution (spec: "PVE Turn Resolution"). Unlike
 * {@link PvpBattleService}, resolves UP TO TWO attacks in a single call: the
 * player always attacks first, and the machine only gets a counter-attack in
 * the SAME call if the player did not finish it off (design D6: PVE and PVP
 * are genuinely different algorithms, not unified behind a shared
 * interface — they share only {@link AttackRoller} and {@link
 * BattleFinisher}). Only the initiator may act — there is no second human.
 */
@Service
public class PveBattleService {

    private static final String NOT_PVE_MODE_MESSAGE = "Esta accion solo esta disponible para batallas PVE";
    private static final String BATTLE_NOT_FOUND_MESSAGE = "Batalla no encontrada";
    private static final String NOT_YOUR_BATTLE_MESSAGE = "no puedes jugar el turno de una batalla ajena";

    private final BattleRepository battleRepository;
    private final AttackRoller attackRoller;
    private final BattleFinisher battleFinisher;
    private final BattleAccessChecker battleAccessChecker;

    public PveBattleService(BattleRepository battleRepository, AttackRoller attackRoller,
            BattleFinisher battleFinisher, BattleAccessChecker battleAccessChecker) {
        this.battleRepository = battleRepository;
        this.attackRoller = attackRoller;
        this.battleFinisher = battleFinisher;
        this.battleAccessChecker = battleAccessChecker;
    }

    @Transactional
    public PveTurnResultView applyTurn(Long actorUserId, Long battleId) {
        Battle battle = battleRepository.findWithAssociationsById(battleId)
                .orElseThrow(() -> new NotFoundException(BATTLE_NOT_FOUND_MESSAGE));

        // Participant check comes first, before any business-state rule, so a
        // non-participant probing this endpoint always gets 403 regardless of
        // the battle's actual mode/status — mirroring BattleQueryService's
        // assertCanView-right-after-fetch pattern instead of leaking battle
        // state via a revealing 400. BattleAccessChecker#isParticipant
        // degrades correctly here since opponentUser is always null for PVE
        // battles, so this is equivalent to an initiator-only check.
        if (!battleAccessChecker.isParticipant(battle, actorUserId)) {
            throw new ForbiddenException(NOT_YOUR_BATTLE_MESSAGE);
        }

        BattleRules.assertMode(battle.getMode(), BattleMode.PVE, NOT_PVE_MODE_MESSAGE);
        BattleRules.assertInProgressForTurn(battle.getStatus());

        PveAttackView playerAttack = resolvePlayerAttack(battle);

        if (battle.getOpponentCurrentHp() == 0) {
            Battle finished = battleFinisher.finishWithHumanWinnerAgainstMachine(battle, battle.getInitiatorUser());
            return new PveTurnResultView(playerAttack, null, BattleView.from(finished));
        }

        PveAttackView machineAttack = resolveMachineCounterAttack(battle);

        if (battle.getInitiatorCurrentHp() == 0) {
            Battle finished = battleFinisher.finishWithMachineWinner(battle, battle.getInitiatorUser());
            return new PveTurnResultView(playerAttack, machineAttack, BattleView.from(finished));
        }

        battle.setTurnNumber(battle.getTurnNumber() + 1);
        Battle saved = battleRepository.save(battle);
        return new PveTurnResultView(playerAttack, machineAttack, BattleView.from(saved));
    }

    private PveAttackView resolvePlayerAttack(Battle battle) {
        Character playerCharacter = battle.getInitiatorCharacter();
        AttackRoll roll = attackRoller.roll(playerCharacter.getAttack());
        int damage = roll.rolledAttack();
        int remainingHp = Math.max(0, battle.getOpponentCurrentHp() - damage);
        battle.setOpponentCurrentHp(remainingHp);
        return toAttackView(roll, playerCharacter.getAttack(), damage);
    }

    private PveAttackView resolveMachineCounterAttack(Battle battle) {
        Character machineCharacter = battle.getOpponentCharacter();
        AttackRoll roll = attackRoller.roll(machineCharacter.getAttack());
        int damage = roll.rolledAttack();
        int remainingHp = Math.max(0, battle.getInitiatorCurrentHp() - damage);
        battle.setInitiatorCurrentHp(remainingHp);
        return toAttackView(roll, machineCharacter.getAttack(), damage);
    }

    private PveAttackView toAttackView(AttackRoll roll, int baseAttack, int damage) {
        return new PveAttackView(roll.level(), baseAttack, roll.rolledAttack(), damage);
    }
}
