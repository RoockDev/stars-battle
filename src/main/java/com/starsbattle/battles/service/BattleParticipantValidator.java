package com.starsbattle.battles.service;

import com.starsbattle.battles.domain.BattleRules;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Shared "user exists, character exists, user.level &gt;= character
 * .levelRequired" validation reused by {@code BattleCreationService}'s
 * start-PVE, start-PVP, and join-PVP flows (spec: "Start PVE Battle", "Start
 * PVP Battle", "Join PVP Battle" — all three share the identical
 * user/character/level-gate check). Extracted so the three call sites don't
 * duplicate the same lookups and guard.
 */
@Component
public class BattleParticipantValidator {

    private static final String USER_NOT_FOUND_MESSAGE = "Usuario no encontrado";
    private static final String CHARACTER_NOT_FOUND_MESSAGE = "Personaje no encontrado";

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;

    public BattleParticipantValidator(UserRepository userRepository, CharacterRepository characterRepository) {
        this.userRepository = userRepository;
        this.characterRepository = characterRepository;
    }

    public ValidatedParticipant validateParticipant(Long userId, Long characterId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND_MESSAGE));
        Character character = requireCharacter(characterId);
        BattleRules.assertLevelGate(user.getLevel(), character.getLevelRequired());
        return new ValidatedParticipant(user, character);
    }

    public Character requireCharacter(Long characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new NotFoundException(CHARACTER_NOT_FOUND_MESSAGE));
    }

    public record ValidatedParticipant(User user, Character character) {
    }
}
