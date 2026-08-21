package com.starsbattle.users.repository;

import com.starsbattle.users.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Ordered wins DESC, losses ASC, xp DESC, with {@code id ASC} as a final
     * deterministic tiebreaker — freshly-registered users all default to
     * wins=0/losses=0/xp=0, so ties are genuinely reachable, and Postgres
     * gives no ordering guarantee between tied rows without an explicit
     * tiebreaker. {@code id ASC} keeps the sequential {@code rank} field
     * stable across repeated calls (first-registered ranks first among
     * ties).
     */
    List<User> findAllByOrderByWinsDescLossesAscXpDescIdAsc(Pageable pageable);
}
