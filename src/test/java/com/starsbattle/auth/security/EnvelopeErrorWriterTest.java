package com.starsbattle.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the security-filter-level error body is written as UTF-8, matching
 * Spring MVC's Jackson converter — the servlet spec default is ISO-8859-1,
 * which would mis-encode the accented Spanish messages this codebase uses
 * elsewhere (e.g. a future non-ASCII message routed through this writer).
 */
class EnvelopeErrorWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesUtf8CharacterEncoding() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        EnvelopeErrorWriter.write(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "sin permiso: ñ á é");

        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("sin permiso: ñ á é");
    }
}
