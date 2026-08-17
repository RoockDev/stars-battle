package com.starsbattle.battles.dto;

import jakarta.validation.constraints.NotNull;

public record StartPvpRequest(@NotNull Long myCharacterId) {
}
