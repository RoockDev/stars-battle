package com.starsbattle.auth.service;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Issues signed JWTs carrying {@code sub}/{@code email}/{@code roles} claims
 * (design: "Core Interfaces" / D1). Framework-adjacent (uses Spring Security
 * types) but has no Spring stereotype annotations itself — it is wired via a
 * {@code @Bean} factory method in {@code JwtBeansConfig} so it stays trivial
 * to unit test with a hand-built {@link JwtEncoder}.
 *
 * <p>The JWS header explicitly requests {@link MacAlgorithm#HS256}:
 * {@link JwtEncoderParameters#from(JwtClaimsSet)} alone defaults to RS256
 * (asymmetric), which fails JWK selection against a symmetric secret key
 * source.
 */
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final Duration expiration;

    public JwtIssuer(JwtEncoder encoder, Duration expiration) {
        this.encoder = encoder;
        this.expiration = expiration;
    }

    public String issue(Long userId, String email, List<String> roles) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        List<String> safeRoles = roles != null ? roles : List.of();
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", safeRoles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
