package com.starsbattle.users.repository;

import com.starsbattle.testsupport.AbstractDataJpaTest;
import com.starsbattle.users.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryTest extends AbstractDataJpaTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findByNameReturnsSeededUserRole() {
        Optional<Role> role = roleRepository.findByName("USER");

        assertThat(role).isPresent();
        assertThat(role.get().getName()).isEqualTo("USER");
    }

    @Test
    void findByNameIsEmptyForUnknownRole() {
        Optional<Role> role = roleRepository.findByName("SUPERADMIN");

        assertThat(role).isEmpty();
    }
}
