package com.starsbattle.characters.service;

import com.starsbattle.characters.dto.CharacterResponse;
import com.starsbattle.characters.repository.CharacterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Roster listing (spec: "List Roster") — read-only, no mutation ever happens here. */
@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> listAll() {
        return characterRepository.findAllByOrderByIdAsc().stream()
                .map(CharacterResponse::from)
                .toList();
    }
}
