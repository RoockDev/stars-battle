package com.starsbattle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Maps to HTTP 403 in {@code GlobalExceptionHandler}. Used for
 * authorization failures the application detects itself (e.g. "No es tu
 * turno") as opposed to Spring Security's own {@code AccessDeniedException}.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
