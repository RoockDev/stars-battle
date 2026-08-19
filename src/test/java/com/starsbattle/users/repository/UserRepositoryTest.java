package com.starsbattle.users.repository;

import com.starsbattle.testsupport.AbstractDataJpaTest;
import com.starsbattle.users.domain.Role;
import com.starsbattle.users.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void rankingOrdersByWinsDescThenLossesAscThenXpDesc() {
        User mostWins = newUser("most-wins@batalla.com", 10, 5, 10);
        User fewerLossesSameWins = newUser("fewer-losses@batalla.com", 5, 1, 999);
        User moreLossesSameWins = newUser("more-losses@batalla.com", 5, 2, 50);
        userRepository.saveAll(List.of(moreLossesSameWins, mostWins, fewerLossesSameWins));

        List<User> ranking = userRepository.findAllByOrderByWinsDescLossesAscXpDescIdAsc(PageRequest.of(0, 10));

        assertThat(ranking).extracting(User::getEmail)
                .containsExactly("most-wins@batalla.com", "fewer-losses@batalla.com", "more-losses@batalla.com");
    }

    @Test
    void rankingBreaksTiesOnWinsAndLossesByXpDesc() {
        User lowerXp = newUser("lower-xp@batalla.com", 3, 1, 20);
        User higherXp = newUser("higher-xp@batalla.com", 3, 1, 80);
        userRepository.saveAll(List.of(lowerXp, higherXp));

        List<User> ranking = userRepository.findAllByOrderByWinsDescLossesAscXpDescIdAsc(PageRequest.of(0, 10));

        assertThat(ranking).extracting(User::getEmail)
                .containsExactly("higher-xp@batalla.com", "lower-xp@batalla.com");
    }

    @Test
    void rankingBreaksFullTiesByIdAscForDeterministicOrder() {
        User tiedFirstRegistered = newUser("tied-first@batalla.com", 0, 0, 0);
        User tiedSecondRegistered = newUser("tied-second@batalla.com", 0, 0, 0);
        User tiedThirdRegistered = newUser("tied-third@batalla.com", 0, 0, 0);
        userRepository.saveAll(List.of(tiedFirstRegistered, tiedSecondRegistered, tiedThirdRegistered));

        List<Long> expectedOrder = List.of(
                tiedFirstRegistered.getId(), tiedSecondRegistered.getId(), tiedThirdRegistered.getId());

        for (int i = 0; i < 5; i++) {
            List<User> ranking = userRepository.findAllByOrderByWinsDescLossesAscXpDescIdAsc(PageRequest.of(0, 10));

            assertThat(ranking).extracting(User::getId).containsExactlyElementsOf(expectedOrder);
        }
    }

    @Test
    void deletingUserCascadesRemovalOfUserRolesButKeepsTheRole() {
        Role userRole = fetchRoleByName("USER");
        User user = new User("cascade-target@batalla.com", "hashed-password");
        user.addRole(userRole);
        user = userRepository.saveAndFlush(user);
        Long userId = user.getId();

        assertThat(countUserRoles(userId)).isEqualTo(1);

        userRepository.delete(user);
        userRepository.flush();

        assertThat(countUserRoles(userId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM roles WHERE name = 'USER'", Integer.class)).isEqualTo(1);
    }

    private Role fetchRoleByName(String name) {
        EntityManager entityManager = testEntityManager.getEntityManager();
        return entityManager.createQuery("select r from Role r where r.name = :name", Role.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    private int countUserRoles(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_roles WHERE user_id = ?", Integer.class, userId);
    }

    private User newUser(String email, int wins, int losses, int xp) {
        User user = new User(email, "hashed-password");
        user.setWins(wins);
        user.setLosses(losses);
        user.setXp(xp);
        return user;
    }
}
