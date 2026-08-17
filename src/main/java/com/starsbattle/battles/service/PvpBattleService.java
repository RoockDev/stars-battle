package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.AttackRoll;
import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleRules;
import com.starsbattle.battles.domain.BattleTurn;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.TurnResultView;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * PVP turn resolution (spec: "PVP Turn Resolution"). Resolves exactly one
 * attack per call and either alternates {@code nextTurn} or — on a knockout
 * — delegates to {@link BattleFinisher} inside this same transaction
 * (design D6: PVE resolves two attacks per call and is a genuinely
 * different sibling service, {@code PveBattleService}, PR12; the two share
 * only {@link AttackRoller} and {@link BattleFinisher}).
 */
@Service
public class PvpBattleService {

    private static final String NOT_PVP_MODE_MESSAGE = "Esta accion solo esta disponible para batallas PVP";
    private static final String BATTLE_NOT_FOUND_MESSAGE = "Batalla no encontrada";
    private static final String NOT_YOUR_TURN_MESSAGE = "No es tu turno";

    private final BattleRepository battleRepository;
    private final AttackRoller attackRoller;
    private final BattleFinisher battleFinisher;

    public PvpBattleService(BattleRepository battleRepository, AttackRoller attackRoller,
            BattleFinisher battleFinisher) {
        this.battleRepository = battleRepository;
        this.attackRoller = attackRoller;
        this.battleFinisher = battleFinisher;
    }

    @Transactional
    public TurnResultView applyTurn(Long actorUserId, Long battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new NotFoundException(BATTLE_NOT_FOUND_MESSAGE));

        BattleRules.assertMode(battle.getMode(), BattleMode.PVP, NOT_PVP_MODE_MESSAGE);
        BattleRules.assertInProgressForTurn(battle.getStatus());

        BattleTurn actorSide = resolveActorSide(battle, actorUserId);
        if (actorSide != battle.getNextTurn()) {
            throw new ForbiddenException(NOT_YOUR_TURN_MESSAGE);
        }

        Character attackerCharacter = actorSide == BattleTurn.INITIATOR
                ? battle.getInitiatorCharacter() : battle.getOpponentCharacter();
        int baseAttack = attackerCharacter.getAttack();
        AttackRoll roll = attackRoller.roll(baseAttack);
        int damage = roll.rolledAttack();

        int defenderRemainingHp = applyDamageToDefender(battle, actorSide, damage);

        Battle resultBattle;
        if (defenderRemainingHp == 0) {
            resultBattle = finishBattle(battle, actorSide);
        } else {
            advanceTurn(battle, actorSide);
            resultBattle = battleRepository.save(battle);
        }

        return new TurnResultView(actorSide, roll.level(), baseAttack, roll.rolledAttack(), damage,
                BattleView.from(resultBattle));
    }

    private BattleTurn resolveActorSide(Battle battle, Long actorUserId) {
        if (Objects.equals(actorUserId, battle.getInitiatorUser().getId())) {
            return BattleTurn.INITIATOR;
        }
        if (battle.getOpponentUser() != null && Objects.equals(actorUserId, battle.getOpponentUser().getId())) {
            return BattleTurn.OPPONENT;
        }
        throw new ForbiddenException(NOT_YOUR_TURN_MESSAGE);
    }

    private int applyDamageToDefender(Battle battle, BattleTurn actorSide, int damage) {
        int remainingHp;
        if (actorSide == BattleTurn.INITIATOR) {
            remainingHp = Math.max(0, battle.getOpponentCurrentHp() - damage);
            battle.setOpponentCurrentHp(remainingHp);
        } else {
            remainingHp = Math.max(0, battle.getInitiatorCurrentHp() - damage);
            battle.setInitiatorCurrentHp(remainingHp);
        }
        return remainingHp;
    }

    private void advanceTurn(Battle battle, BattleTurn actorSide) {
        battle.setTurnNumber(battle.getTurnNumber() + 1);
        battle.setNextTurn(actorSide == BattleTurn.INITIATOR ? BattleTurn.OPPONENT : BattleTurn.INITIATOR);
    }

    private Battle finishBattle(Battle battle, BattleTurn actorSide) {
        User winner = actorSide == BattleTurn.INITIATOR ? battle.getInitiatorUser() : battle.getOpponentUser();
        User loser = actorSide == BattleTurn.INITIATOR ? battle.getOpponentUser() : battle.getInitiatorUser();
        return battleFinisher.finishWithHumanWinner(battle, winner, loser);
    }
}
