package com.starsbattle.auth.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT signing/verification config. {@code secret} has no fallback default in
 * {@code application.yml} ({@code ${JWT_SECRET}}) and is additionally
 * {@code @NotBlank}-validated here, so the application context refuses to
 * start if it is unset or blank — a deliberate deviation from the source,
 * which fell back to a hardcoded insecure secret when the env var was
 * missing. {@code isSecretLongEnough()} and {@code isExpirationPositive()}
 * are additional {@code @AssertTrue} checks (evaluated by the same
 * {@code @Validated} startup validation as the field-level constraints
 * above) so a too-short HS256 secret or a non-positive expiration also fail
 * fast at startup instead of surfacing later as a confusing runtime error.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {

    private static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "jwt.secret must be at least 256 bits (32 bytes) long for HS256")
    boolean isSecretLongEnough() {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
    }

    @AssertTrue(message = "jwt.expiration must be positive")
    boolean isExpirationPositive() {
        return expiration != null && !expiration.isNegative() && !expiration.isZero();
    }
}
