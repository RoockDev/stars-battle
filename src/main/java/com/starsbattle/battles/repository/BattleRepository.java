package com.starsbattle.battles.repository;

import com.starsbattle.battles.domain.Battle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BattleRepository extends JpaRepository<Battle, Long> {

    /**
     * Loads a single Battle together with all five of its lazy
     * {@code @ManyToOne} associations (initiatorUser, opponentUser,
     * winnerUser, initiatorCharacter, opponentCharacter) in one round trip,
     * instead of the N+1 pattern a plain {@code findById} produces once
     * those associations are navigated (e.g. building a {@code BattleView}).
     */
    @Query("select b from Battle b "
            + "left join fetch b.initiatorUser "
            + "left join fetch b.opponentUser "
            + "left join fetch b.winnerUser "
            + "left join fetch b.initiatorCharacter "
            + "left join fetch b.opponentCharacter "
            + "where b.id = :id")
    Optional<Battle> findWithAssociationsById(@Param("id") Long id);
}
