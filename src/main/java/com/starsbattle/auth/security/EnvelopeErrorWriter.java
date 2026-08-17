package com.starsbattle.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.starsbattle.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Shared body writer for the security-filter-level 401/403 handlers (design
 * D2). These run before {@code GlobalExceptionHandler} could ever see the
 * exception — Spring Security's default entry point / access-denied handler
 * would otherwise emit a bare RFC-6750 body, not the {@link ApiResponse}
 * envelope every other error path uses.
 */
final class EnvelopeErrorWriter {

    private EnvelopeErrorWriter() {
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
