package com.starsbattle.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles missing/invalid JWT on a protected route (design D2): 401 with the
 * shared {@link com.starsbattle.common.ApiResponse} envelope, message
 * "No autenticado" — distinct from the "Credenciales invalidas" message a
 * failed login produces via {@code GlobalExceptionHandler}.
 */
@Component
public class EnvelopeAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MESSAGE = "No autenticado";

    private final ObjectMapper objectMapper;

    public EnvelopeAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        EnvelopeErrorWriter.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, MESSAGE);
    }
}
