package com.starsbattle.characters.repository;

import com.starsbattle.characters.domain.Character;
import com.starsbattle.testsupport.AbstractDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private CharacterRepository characterRepository;

    @Test
    void findAllByOrderByIdAscReturnsFullRosterInIdOrder() {
        List<Character> roster = characterRepository.findAllByOrderByIdAsc();

        assertThat(roster).hasSize(12);
        assertThat(roster.get(0).getName()).isEqualTo("Luke Skywalker");
        assertThat(roster.get(0).getHp()).isEqualTo(100);
        assertThat(roster.get(0).getAttack()).isEqualTo(20);
        assertThat(roster.get(11).getName()).isEqualTo("Rey Skywalker");
        assertThat(roster.get(11).getLevelRequired()).isEqualTo(5);

        for (int i = 1; i < roster.size(); i++) {
            assertThat(roster.get(i).getId()).isGreaterThan(roster.get(i - 1).getId());
        }
    }

    @Test
    void findByIdReturnsPersistedStatsVerbatim() {
        Character vader = characterRepository.findAllByOrderByIdAsc().stream()
                .filter(c -> c.getName().equals("Darth Vader"))
                .findFirst()
                .orElseThrow();

        Character reloaded = characterRepository.findById(vader.getId()).orElseThrow();

        assertThat(reloaded.getHp()).isEqualTo(140);
        assertThat(reloaded.getBaseHp()).isEqualTo(140);
        assertThat(reloaded.getAttack()).isEqualTo(30);
        assertThat(reloaded.getLevelRequired()).isEqualTo(3);
    }
}
