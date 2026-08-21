package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleRules;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.dto.BattleView;
import com.starsbattle.battles.dto.JoinPvpRequest;
import com.starsbattle.battles.dto.StartPveRequest;
import com.starsbattle.battles.dto.StartPvpRequest;
import com.starsbattle.battles.event.BattleUpdatedEvent;
import com.starsbattle.battles.repository.BattleRepository;
import com.starsbattle.battles.service.BattleParticipantValidator.ValidatedParticipant;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.common.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Battle creation and PVP join (spec: "Start PVE Battle", "Start PVP
 * Battle", "Join PVP Battle"). User/character/level-gate validation is
 * delegated to {@link BattleParticipantValidator}, shared across all three
 * flows so the same three checks aren't duplicated three times.
 */
@Service
public class BattleCreationService {

    private static final String SAME_CHARACTER_MESSAGE =
            "El personaje del jugador y el de la maquina deben ser diferentes";
    private static final String MACHINE_CHARACTER_NOT_FOUND_MESSAGE = "Personaje de la maquina no encontrado";
    private static final String BATTLE_NOT_FOUND_MESSAGE = "Batalla no encontrada";
    private static final String NOT_PVP_MODE_MESSAGE = "Esta batalla no es de modo PVP";
    private static final String NOT_WAITING_MESSAGE = "Esta batalla ya no esta esperando oponente";
    private static final String OWN_BATTLE_MESSAGE = "No puedes unirte a tu propia batalla";
    private static final String OPPONENT_ALREADY_EXISTS_MESSAGE = "Esta batalla ya tiene un oponente";
    private static final String MIRROR_CHARACTER_MESSAGE = "No puedes usar el mismo personaje que el iniciador";

    private final BattleParticipantValidator participantValidator;
    private final CharacterRepository characterRepository;
    private final BattleRepository battleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BattleCreationService(BattleParticipantValidator participantValidator,
            CharacterRepository characterRepository, BattleRepository battleRepository,
            ApplicationEventPublisher eventPublisher) {
        this.participantValidator = participantValidator;
        this.characterRepository = characterRepository;
        this.battleRepository = battleRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BattleView startPve(Long userId, StartPveRequest request) {
        ValidatedParticipant participant = participantValidator.validateParticipant(userId, request.myCharacterId());
        Character machineCharacter = characterRepository.findById(request.machineCharacterId())
                .orElseThrow(() -> new NotFoundException(MACHINE_CHARACTER_NOT_FOUND_MESSAGE));

        // Existence (both characters) is validated above, BEFORE this
        // business-rule check — otherwise two identical nonexistent
        // character ids would surface as 400 "must differ" instead of the
        // more accurate 404 "not found".
        BattleRules.assertDistinctCharacters(request.myCharacterId(), request.machineCharacterId(),
                SAME_CHARACTER_MESSAGE);

        Battle battle = new Battle(BattleMode.PVE, participant.user(), participant.character());
        battle.setOpponentCharacter(machineCharacter);
        battle.setInitiatorCurrentHp(participant.character().getHp());
        battle.setOpponentCurrentHp(machineCharacter.getHp());
        battle.setStatus(BattleStatus.IN_PROGRESS);

        return BattleView.from(battleRepository.save(battle));
    }

    @Transactional
    public BattleView startPvp(Long userId, StartPvpRequest request) {
        ValidatedParticipant participant = participantValidator.validateParticipant(userId, request.myCharacterId());

        Battle battle = new Battle(BattleMode.PVP, participant.user(), participant.character());
        battle.setInitiatorCurrentHp(participant.character().getHp());
        battle.setOpponentCurrentHp(0);
        battle.setStatus(BattleStatus.WAITING);

        return BattleView.from(battleRepository.save(battle));
    }

    @Transactional
    public BattleView joinPvp(Long actorUserId, Long battleId, JoinPvpRequest request) {
        Battle battle = battleRepository.findWithAssociationsById(battleId)
                .orElseThrow(() -> new NotFoundException(BATTLE_NOT_FOUND_MESSAGE));

        BattleRules.assertMode(battle.getMode(), BattleMode.PVP, NOT_PVP_MODE_MESSAGE);
        BattleRules.assertStatus(battle.getStatus(), BattleStatus.WAITING, NOT_WAITING_MESSAGE);
        BattleRules.assertActorIsNotInitiator(actorUserId, battle.getInitiatorUser().getId(), OWN_BATTLE_MESSAGE);
        BattleRules.assertNoOpponentYet(battle.getOpponentUser() != null, OPPONENT_ALREADY_EXISTS_MESSAGE);
        BattleRules.assertDistinctCharacters(request.myCharacterId(), battle.getInitiatorCharacter().getId(),
                MIRROR_CHARACTER_MESSAGE);

        ValidatedParticipant participant =
                participantValidator.validateParticipant(actorUserId, request.myCharacterId());

        battle.setOpponentUser(participant.user());
        battle.setOpponentCharacter(participant.character());
        battle.setOpponentCurrentHp(participant.character().getHp());
        battle.setStatus(BattleStatus.IN_PROGRESS);

        // Dirty-checking flushes at transaction commit, bumping @Version — an
        // OptimisticLockingFailureException from a concurrent join is thrown
        // there (outside this method) and mapped to 409 by
        // GlobalExceptionHandler; nothing here catches or swallows it.
        Battle saved = battleRepository.save(battle);
        BattleView view = BattleView.from(saved);
        eventPublisher.publishEvent(new BattleUpdatedEvent(saved.getId(), BattleUpdatedEvent.Type.BATTLE_JOINED, view));
        return view;
    }
}
