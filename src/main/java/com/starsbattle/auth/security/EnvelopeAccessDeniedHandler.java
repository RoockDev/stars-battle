package com.starsbattle.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles an authenticated user lacking the required role (design D2/D3):
 * 403 with the shared envelope, generic Spanish message — matches the same
 * fallback text {@code GlobalExceptionHandler} uses for
 * {@code AccessDeniedException} thrown outside the security filter chain,
 * so both paths agree.
 */
@Component
public class EnvelopeAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MESSAGE = "No tienes permiso para realizar esta accion";

    private final ObjectMapper objectMapper;

    public EnvelopeAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        EnvelopeErrorWriter.write(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, MESSAGE);
    }
}
