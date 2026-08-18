package com.starsbattle.battles.repository;

import com.starsbattle.battles.domain.Battle;
import com.starsbattle.battles.domain.BattleMode;
import com.starsbattle.battles.domain.BattleStatus;
import com.starsbattle.battles.domain.BattleTurn;
import com.starsbattle.characters.domain.Character;
import com.starsbattle.characters.repository.CharacterRepository;
import com.starsbattle.testsupport.AbstractDataJpaTest;
import com.starsbattle.users.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BattleRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private BattleRepository battleRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void savedBattleDefaultsMatchDesign() {
        User initiator = persistUser("initiator-1@batalla.com");
        Character character = firstSeededCharacter();

        Battle battle = new Battle(BattleMode.PVE, initiator, character);
        battle = battleRepository.saveAndFlush(battle);

        assertThat(battle.getId()).isNotNull();
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThat(battle.getTurnNumber()).isEqualTo(1);
        assertThat(battle.getNextTurn()).isEqualTo(BattleTurn.INITIATOR);
        assertThat(battle.getVersion()).isEqualTo(0L);
    }

    @Test
    void versionIncrementsOnEveryUpdateAfterReload() {
        User initiator = persistUser("initiator-2@batalla.com");
        Character character = firstSeededCharacter();

        Battle battle = new Battle(BattleMode.PVP, initiator, character);
        battle = battleRepository.saveAndFlush(battle);
        Long id = battle.getId();
        assertThat(battle.getVersion()).isEqualTo(0L);

        testEntityManager.clear();
        Battle reloaded = battleRepository.findById(id).orElseThrow();
        reloaded.setTurnNumber(2);
        battleRepository.saveAndFlush(reloaded);

        testEntityManager.clear();
        Battle afterFirstUpdate = battleRepository.findById(id).orElseThrow();
        assertThat(afterFirstUpdate.getVersion()).isEqualTo(1L);

        afterFirstUpdate.setTurnNumber(3);
        battleRepository.saveAndFlush(afterFirstUpdate);

        testEntityManager.clear();
        Battle afterSecondUpdate = battleRepository.findById(id).orElseThrow();
        assertThat(afterSecondUpdate.getVersion()).isEqualTo(2L);
    }

    @Test
    void associationsNavigateToDistinctPersistedEntities() {
        User initiator = persistUser("initiator-assoc@batalla.com");
        User opponent = persistUser("opponent-assoc@batalla.com");
        List<Character> roster = orderedRoster();
        Character initiatorCharacter = roster.get(0);
        Character opponentCharacter = roster.get(1);

        Battle battle = new Battle(BattleMode.PVP, initiator, initiatorCharacter);
        battle.setOpponentUser(opponent);
        battle.setOpponentCharacter(opponentCharacter);
        battle.setWinnerUser(opponent);
        battle = battleRepository.saveAndFlush(battle);
        Long id = battle.getId();

        testEntityManager.clear();
        Battle reloaded = battleRepository.findById(id).orElseThrow();

        assertThat(reloaded.getInitiatorUser().getId()).isEqualTo(initiator.getId());
        assertThat(reloaded.getInitiatorUser().getEmail()).isEqualTo("initiator-assoc@batalla.com");
        assertThat(reloaded.getOpponentUser().getId()).isEqualTo(opponent.getId());
        assertThat(reloaded.getWinnerUser().getId()).isEqualTo(opponent.getId());
        assertThat(reloaded.getInitiatorCharacter().getId()).isEqualTo(initiatorCharacter.getId());
        assertThat(reloaded.getInitiatorCharacter().getName()).isEqualTo(initiatorCharacter.getName());
        assertThat(reloaded.getOpponentCharacter().getId()).isEqualTo(opponentCharacter.getId());
    }

    private User persistUser(String email) {
        User user = new User(email, "hashed-password");
        return testEntityManager.persistAndFlush(user);
    }

    private Character firstSeededCharacter() {
        return orderedRoster().get(0);
    }

    private List<Character> orderedRoster() {
        return characterRepository.findAllByOrderByIdAsc();
    }
}
