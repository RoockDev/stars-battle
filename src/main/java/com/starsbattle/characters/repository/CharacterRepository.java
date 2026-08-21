package com.starsbattle.characters.repository;

import com.starsbattle.characters.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    List<Character> findAllByOrderByIdAsc();
}
