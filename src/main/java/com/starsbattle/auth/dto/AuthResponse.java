package com.starsbattle.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response shape for both /auth/register and /auth/login (spec + source parity: snake_case access_token). */
public record AuthResponse(@JsonProperty("access_token") String accessToken, UserSummary user) {
}
