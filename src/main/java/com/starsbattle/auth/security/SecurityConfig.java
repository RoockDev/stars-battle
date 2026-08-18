package com.starsbattle.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The whole filter chain (design D1-D3): stateless JWT resource server
 * (HS256, roles claim mapped to {@code ROLE_*} authorities via
 * {@link JwtAuthenticationConverter} from {@link JwtBeansConfig}), envelope
 * -shaped 401 responses instead of Spring's raw defaults (403s from
 * {@code @PreAuthorize} are resolved by {@code GlobalExceptionHandler}, not
 * this filter chain), and method-level RBAC via {@code @PreAuthorize}
 * ({@link EnableMethodSecurity}). Only {@code POST /auth/register},
 * {@code POST /auth/login}, {@code /actuator/health}, and the
 * {@code /ws/**} WebSocket handshake are public; everything else requires a
 * valid JWT.
 *
 * <p>{@code authenticationEntryPoint} is registered in BOTH
 * {@code oauth2ResourceServer(...)} and {@code exceptionHandling(...)} —
 * empirically NOT redundant: {@code oauth2ResourceServer()}'s registration
 * scopes a {@code DelegatingAuthenticationEntryPoint} entry specifically to
 * requests carrying an {@code Authorization: Bearer} header (an invalid
 * token); the {@code exceptionHandling()} one is the fallback used for
 * requests with no such header at all (a missing token). Dropping the
 * {@code oauth2ResourceServer()} registration was tried and verified (via
 * {@code SecurityFilterChainIT.invalidTokenOnProtectedRouteReturns401WithEnvelope})
 * to make Spring fall back to its own default
 * {@code BearerTokenAuthenticationEntryPoint} for the invalid-token case,
 * breaking the envelope-shaped 401 body — so both registrations stay.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final EnvelopeAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationConverter jwtAuthenticationConverter,
            EnvelopeAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/ws/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint));

        return http.build();
    }
}
