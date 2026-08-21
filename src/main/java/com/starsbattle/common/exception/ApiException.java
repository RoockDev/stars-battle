package com.starsbattle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for application-level exceptions that map directly to an HTTP
 * status in {@code GlobalExceptionHandler}. Concrete subclasses stay named
 * (e.g. {@link NotFoundException}) for call-site readability — {@code throw
 * new NotFoundException(...)} reads better than throwing a generic exception
 * with a status argument — while the status-mapping plumbing lives here
 * once instead of being duplicated per subclass and per handler method.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
