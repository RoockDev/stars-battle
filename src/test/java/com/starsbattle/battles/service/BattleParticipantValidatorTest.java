package com.starsbattle.battles.service;

import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.common.exception.NotFoundException;
import com.starsbattle.users.domain.User;
import com.starsbattle.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repositories, no Spring context. Shared user-exists +
 * character-exists + level-gate validation reused by
 * {@code BattleCreationService}'s start-PVE/start-PVP/join-PVP flows — this
 * is the single reusable component the task explicitly asked for instead of
 * duplicating the same three checks three times.
 */
@ExtendWith(MockitoExtension.class)
class BattleParticipantValidatorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    private BattleParticipantValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BattleParticipantValidator(userRepository, characterRepository);
    }

    @Test
    void validateParticipantReturnsUserAndCharacterWhenLevelSufficient() {
        User user = userWithLevel(4);
        Character character = characterWithLevelRequired(3);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(character));

        BattleParticipantValidator.ValidatedParticipant result = validator.validateParticipant(1L, 10L);

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.character()).isEqualTo(character);
    }

    @Test
    void validateParticipantRejectsInsufficientLevel() {
        User user = userWithLevel(2);
        Character character = characterWithLevelRequired(5);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.of(character));

        assertThatThrownBy(() -> validator.validateParticipant(1L, 10L))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void validateParticipantThrowsNotFoundWhenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateParticipant(1L, 10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void validateParticipantThrowsNotFoundWhenCharacterMissing() {
        User user = userWithLevel(4);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(characterRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateParticipant(1L, 10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void requireCharacterReturnsCharacterWhenPresent() {
        Character character = characterWithLevelRequired(1);
        when(characterRepository.findById(20L)).thenReturn(Optional.of(character));

        assertThat(validator.requireCharacter(20L)).isEqualTo(character);
    }

    @Test
    void requireCharacterThrowsNotFoundWhenMissing() {
        when(characterRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.requireCharacter(20L))
                .isInstanceOf(NotFoundException.class);
    }

    private User userWithLevel(int level) {
        User user = new User("participant@batalla.com", "hashed-password");
        user.setLevel(level);
        return user;
    }

    private Character characterWithLevelRequired(int levelRequired) {
        return new Character("Test Character", 100, 100, 20, levelRequired);
    }
}
