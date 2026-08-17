package com.starsbattle.common.exception;

/**
 * Maps to HTTP 400 in {@code GlobalExceptionHandler}. Used for domain rule
 * violations that are not Bean Validation failures (duplicate email, level
 * gate, mirror match, wrong battle mode/status, etc.).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
