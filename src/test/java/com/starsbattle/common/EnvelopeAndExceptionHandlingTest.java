package com.starsbattle.common;

import com.starsbattle.common.web.TestEnvelopeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice proving the global response envelope (success + error
 * mapping table from design/spec) using a throwaway test-only controller —
 * no real feature controller exists yet at this point in the port.
 * Security filters are disabled here because SecurityConfig does not exist
 * until PR5; this stays valid once it lands since addFilters=false simply
 * bypasses the filter chain regardless of what it contains.
 */
@WebMvcTest(controllers = TestEnvelopeController.class)
@AutoConfigureMockMvc(addFilters = false)
class EnvelopeAndExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successResponseUsesDefaultMessageAndWrapsData() throws Exception {
        mockMvc.perform(get("/test/default-message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Operación realizada con éxito"))
                .andExpect(jsonPath("$.data.value").value("ok"));
    }

    @Test
    void successResponseUsesApiMessageAnnotationWhenPresent() throws Exception {
        mockMvc.perform(post("/test/custom-message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Personaje creado con exito"))
                .andExpect(jsonPath("$.data.value").value("created"));
    }

    @Test
    void validationFailureMapsTo400WithFieldMessage() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownFieldRejectedAs400WithSpanishMessage() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Luke\",\"extra\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Campos no permitidos o cuerpo invalido"));
    }

    @Test
    void businessRuleMapsTo400WithRuleMessage() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("El email ya esta registrado"));
    }

    @Test
    void forbiddenMapsTo403WithMessage() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No es tu turno"));
    }

    @Test
    void notFoundMapsTo404WithMessage() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Batalla no encontrada"));
    }

    @Test
    void optimisticLockFailureMapsTo409WithConcurrencyMessage() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("La batalla fue modificada por otra peticion, vuelve a intentarlo"));
    }

    @Test
    void explicitConflictMapsTo409WithItsOwnMessage() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Ya existe una batalla activa"));
    }

    @Test
    void unexpectedExceptionMapsTo500WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }

    @Test
    void authenticationFailureMapsTo401WithMessage() throws Exception {
        mockMvc.perform(get("/test/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    void accessDeniedMapsTo403WithFallbackMessage() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta accion"));
    }

    @Test
    void constraintViolationMapsTo400WithFallbackMessage() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Datos invalidos"));
    }

    @Test
    void unsupportedMethodMapsTo405WithMessage() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Metodo no soportado"));
    }

    @Test
    void unsupportedMediaTypeMapsTo415WithMessage() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Luke"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tipo de contenido no soportado"));
    }

    @Test
    void typeMismatchMapsTo400WithMessage() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Parametro invalido"));
    }

    @Test
    void missingParameterMapsTo400WithMessage() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Parametro requerido faltante"));
    }
}
