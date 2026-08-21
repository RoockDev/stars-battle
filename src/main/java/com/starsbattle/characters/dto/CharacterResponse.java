package com.starsbattle.characters.dto;

import com.starsbattle.characters.domain.Character;

/** Plain projection of a roster entry (spec: "List Roster"). */
public record CharacterResponse(Long id, String name, Integer hp, Integer baseHp, Integer attack,
        Integer levelRequired) {

    public static CharacterResponse from(Character character) {
        return new CharacterResponse(character.getId(), character.getName(), character.getHp(),
                character.getBaseHp(), character.getAttack(), character.getLevelRequired());
    }
}
