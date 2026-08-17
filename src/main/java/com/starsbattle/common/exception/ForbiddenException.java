package com.starsbattle.common.exception;

/**
 * Maps to HTTP 403 in {@code GlobalExceptionHandler}. Used for
 * authorization failures the application detects itself (e.g. "No es tu
 * turno") as opposed to Spring Security's own {@code AccessDeniedException}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
