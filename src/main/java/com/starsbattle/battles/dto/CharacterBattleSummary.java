package com.starsbattle.battles.dto;

import com.starsbattle.characters.domain.Character;

/** Nested character projection used inside {@link BattleView} — never the raw entity. */
public record CharacterBattleSummary(Long id, String name, Integer hp, Integer attack, Integer levelRequired) {

    public static CharacterBattleSummary from(Character character) {
        return new CharacterBattleSummary(
                character.getId(), character.getName(), character.getHp(), character.getAttack(),
                character.getLevelRequired());
    }
}
