package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.AttackRoll;
import com.starsbattle.battles.domain.AttackRoller;
import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleRules;
import com.starsbattle.battles.domain.BattleTurn;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.TurnResultView;
import com.starsbattle.battles.event.BattleUpdatedEvent;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import org.springframework.context.ApplicationEventPublisher;
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
    private final BattleAccessChecker battleAccessChecker;
    private final ApplicationEventPublisher eventPublisher;

    public PvpBattleService(BattleRepository battleRepository, AttackRoller attackRoller,
            BattleFinisher battleFinisher, BattleAccessChecker battleAccessChecker,
            ApplicationEventPublisher eventPublisher) {
        this.battleRepository = battleRepository;
        this.attackRoller = attackRoller;
        this.battleFinisher = battleFinisher;
        this.battleAccessChecker = battleAccessChecker;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TurnResultView applyTurn(Long actorUserId, Long battleId) {
        Battle battle = battleRepository.findWithAssociationsById(battleId)
                .orElseThrow(() -> new NotFoundException(BATTLE_NOT_FOUND_MESSAGE));

        // Participant check comes first, before any business-state rule, so a
        // non-participant probing this endpoint always gets 403 regardless of
        // the battle's actual mode/status — mirroring BattleQueryService's
        // assertCanView-right-after-fetch pattern instead of leaking battle
        // state via a revealing 400.
        if (!battleAccessChecker.isParticipant(battle, actorUserId)) {
            throw new ForbiddenException(NOT_YOUR_TURN_MESSAGE);
        }

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
        boolean battleContinues = defenderRemainingHp != 0;
        if (battleContinues) {
            advanceTurn(battle, actorSide);
            resultBattle = battleRepository.save(battle);
        } else {
            resultBattle = finishBattle(battle, actorSide);
        }

        TurnResultView result = new TurnResultView(actorSide, roll.level(), baseAttack, roll.rolledAttack(), damage,
                BattleView.from(resultBattle));

        if (battleContinues) {
            // The finish path already gets its own BATTLE_FINISHED event
            // from BattleFinisher — only the continuing-turn path publishes
            // here, to avoid a duplicate broadcast for the same turn.
            eventPublisher.publishEvent(
                    new BattleUpdatedEvent(resultBattle.getId(), BattleUpdatedEvent.Type.TURN_APPLIED, result));
        }

        return result;
    }

    /**
     * Picks the caller's side. Only called after {@link BattleAccessChecker
     * #isParticipant} has already confirmed {@code actorUserId} is either the
     * initiator or the opponent, so no further membership check is needed
     * here — that logic lives in {@link BattleAccessChecker} alone.
     */
    private BattleTurn resolveActorSide(Battle battle, Long actorUserId) {
        return Objects.equals(actorUserId, battle.getInitiatorUser().getId())
                ? BattleTurn.INITIATOR
                : BattleTurn.OPPONENT;
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
