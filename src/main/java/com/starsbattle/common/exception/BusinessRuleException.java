package com.starsbattle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Maps to HTTP 400 in {@code GlobalExceptionHandler}. Used for domain rule
 * violations that are not Bean Validation failures (duplicate email, level
 * gate, mirror match, wrong battle mode/status, etc.).
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
