package com.starsbattle.battles.dto;

import jakarta.validation.constraints.NotNull;

public record StartPveRequest(@NotNull Long myCharacterId, @NotNull Long machineCharacterId) {
}
