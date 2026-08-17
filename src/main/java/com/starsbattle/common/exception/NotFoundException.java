package com.starsbattle.common.exception;

/** Maps to HTTP 404 in {@code GlobalExceptionHandler}. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
