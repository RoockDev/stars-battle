package com.starsbattle.integration;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only controller used exclusively by {@code SecurityFilterChainIT} to
 * exercise the real {@code SecurityConfig} end to end (permitAll routes,
 * 401/403 envelope shapes, role-gated routes) — no real feature controller
 * exists yet at this point in the port (auth/characters/battles land in
 * PR6/PR7/PR9-12). Lives under {@code com.starsbattle.integration} so it is
 * picked up by full {@code @SpringBootTest} component scanning without
 * colliding with the real {@code AuthController} package.
 */
@RestController
public class SecureProbeController {

    @GetMapping("/auth/probe")
    public Map<String, String> authProbe() {
        return Map.of("value", "public");
    }

    @GetMapping("/probe/secure")
    public Map<String, String> secureProbe() {
        return Map.of("value", "secure");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/probe/admin")
    public Map<String, String> adminProbe() {
        return Map.of("value", "admin");
    }
}
