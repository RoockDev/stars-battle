package com.starsbattle.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test (no Spring context): constructs a real Nimbus HS256
 * encoder/decoder pair from a raw secret and verifies JwtIssuer produces
 * tokens carrying sub/email/roles claims (design: "Core Interfaces" /
 * "JWT plumbing" D1) with the exact 1h TTL confirmed against the source's
 * auth.module.ts.
 */
class JwtIssuerTest {

    private static final String SECRET = "unit-test-hs256-secret-must-be-at-least-32-bytes-long";

    private final SecretKeySpec secretKey =
            new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

    @Test
    void issuesTokenWithSubjectEmailAndRolesClaims() {
        JwtIssuer issuer = new JwtIssuer(encoder, Duration.ofHours(1));

        String token = issuer.issue(42L, "luke@batalla.com", List.of("USER"));
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("42");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("luke@batalla.com");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
    }

    @Test
    void tokenExpiresExactlyOneHourAfterIssuedAtAndPreservesMultipleRoles() {
        JwtIssuer issuer = new JwtIssuer(encoder, Duration.ofHours(1));

        String token = issuer.issue(7L, "admin@batalla.com", List.of("ADMIN", "USER"));
        Jwt decoded = decoder.decode(token);

        Duration lifetime = Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt());
        assertThat(lifetime).isEqualTo(Duration.ofHours(1));
        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactlyInAnyOrder("ADMIN", "USER");
    }
}
