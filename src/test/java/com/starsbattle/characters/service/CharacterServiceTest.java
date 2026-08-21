package com.starsbattle.characters.service;

import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.dto.CharacterResponse;
import com.starsbattle.characters.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit layer: mocked repository, no Spring context — fast feedback on the
 * roster listing mapping/ordering delegation (spec: "List Roster").
 */
@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private CharacterRepository characterRepository;

    private CharacterService characterService;

    @BeforeEach
    void setUp() {
        characterService = new CharacterService(characterRepository);
    }

    @Test
    void listAllDelegatesToIdOrderedRepositoryQueryAndMapsFieldsVerbatim() {
        Character luke = new Character("Luke Skywalker", 100, 100, 20, 1);
        Character vader = new Character("Darth Vader", 140, 140, 30, 3);
        when(characterRepository.findAllByOrderByIdAsc()).thenReturn(List.of(luke, vader));

        List<CharacterResponse> roster = characterService.listAll();

        verify(characterRepository).findAllByOrderByIdAsc();
        assertThat(roster).extracting(CharacterResponse::name).containsExactly("Luke Skywalker", "Darth Vader");
        assertThat(roster.get(1).hp()).isEqualTo(140);
        assertThat(roster.get(1).baseHp()).isEqualTo(140);
        assertThat(roster.get(1).attack()).isEqualTo(30);
        assertThat(roster.get(1).levelRequired()).isEqualTo(3);
    }

    @Test
    void listAllReturnsEmptyListWhenNoCharactersExist() {
        when(characterRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertThat(characterService.listAll()).isEmpty();
    }
}
