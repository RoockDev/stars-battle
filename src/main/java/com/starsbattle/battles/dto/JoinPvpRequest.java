package com.starsbattle.battles.dto;

import jakarta.validation.constraints.NotNull;

public record JoinPvpRequest(@NotNull Long myCharacterId) {
}
