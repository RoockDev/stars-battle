package com.starsbattle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Maps to HTTP 409 in {@code GlobalExceptionHandler}, alongside
 * {@code OptimisticLockingFailureException}. For application-detected
 * conflicts that are not concurrent-modification races.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
