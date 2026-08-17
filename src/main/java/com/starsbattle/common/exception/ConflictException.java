package com.starsbattle.common.exception;

/**
 * Maps to HTTP 409 in {@code GlobalExceptionHandler}, alongside
 * {@code OptimisticLockingFailureException}. For application-detected
 * conflicts that are not concurrent-modification races.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
