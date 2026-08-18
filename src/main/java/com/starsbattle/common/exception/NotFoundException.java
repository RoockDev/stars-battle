package com.starsbattle.common.exception;

import org.springframework.http.HttpStatus;

/** Maps to HTTP 404 in {@code GlobalExceptionHandler}. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
