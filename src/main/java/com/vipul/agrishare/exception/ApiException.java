package com.vipul.agrishare.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception carrying an HTTP status, so GlobalExceptionHandler can map
 * it correctly without a growing if/else chain of instanceof checks.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException conflict(String message) {
        return new ApiException(message, HttpStatus.CONFLICT);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(message, HttpStatus.UNAUTHORIZED);
    }

    public static ApiException notFound(String message) {
        return new ApiException(message, HttpStatus.NOT_FOUND);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(message, HttpStatus.BAD_REQUEST);
    }
}
