package com.starsbattle.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT signing/verification config. {@code secret} has no fallback default in
 * {@code application.yml} ({@code ${JWT_SECRET}}) and is additionally
 * {@code @NotBlank}-validated here, so the application context refuses to
 * start if it is unset or blank — a deliberate deviation from the source,
 * which fell back to a hardcoded insecure secret when the env var was
 * missing.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {
}
