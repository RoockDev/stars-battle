package com.starsbattle.common.web;

import com.starsbattle.common.ApiMessage;
import com.starsbattle.common.exception.BusinessRuleException;
import com.starsbattle.common.exception.ConflictException;
import com.starsbattle.common.exception.ForbiddenException;
import com.starsbattle.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only controller used exclusively by {@code EnvelopeAndExceptionHandlingTest}
 * (a {@code @WebMvcTest} slice) to exercise {@code EnvelopeResponseBodyAdvice} and
 * {@code GlobalExceptionHandler} without depending on any real feature controller.
 */
@RestController
public class TestEnvelopeController {

    @GetMapping("/test/default-message")
    public Map<String, String> defaultMessage() {
        return Map.of("value", "ok");
    }

    @ApiMessage("Personaje creado con exito")
    @PostMapping("/test/custom-message")
    public Map<String, String> customMessage() {
        return Map.of("value", "created");
    }

    @PostMapping("/test/validate")
    public Map<String, String> validate(@Valid @RequestBody TestPayload payload) {
        return Map.of("value", payload.name());
    }

    @GetMapping("/test/business-rule")
    public void businessRule() {
        throw new BusinessRuleException("El email ya esta registrado");
    }

    @GetMapping("/test/forbidden")
    public void forbidden() {
        throw new ForbiddenException("No es tu turno");
    }

    @GetMapping("/test/not-found")
    public void notFound() {
        throw new NotFoundException("Batalla no encontrada");
    }

    @GetMapping("/test/optimistic-lock")
    public void optimisticLock() {
        throw new OptimisticLockingFailureException("stale");
    }

    @GetMapping("/test/conflict")
    public void conflict() {
        throw new ConflictException("Ya existe una batalla activa");
    }

    @GetMapping("/test/unexpected")
    public void unexpected() {
        throw new IllegalStateException("boom");
    }

    public record TestPayload(@NotBlank String name) {
    }
}
