package com.vipul.agrishare.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception carrying an HTTP status, so GlobalExceptionHandler can map
 * it correctly without a growing if/else chain of instanceof checks.
 *
 * The message is an i18n key (e.g. "error.email.exists") resolved against
 * messages_*.properties in the caller's language by GlobalExceptionHandler.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object[] args;

    public ApiException(String messageKey, HttpStatus status, Object... args) {
        super(messageKey);
        this.status = status;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object[] getArgs() {
        return args;
    }

    public static ApiException conflict(String key, Object... args) {
        return new ApiException(key, HttpStatus.CONFLICT, args);
    }

    public static ApiException unauthorized(String key, Object... args) {
        return new ApiException(key, HttpStatus.UNAUTHORIZED, args);
    }

    public static ApiException forbidden(String key, Object... args) {
        return new ApiException(key, HttpStatus.FORBIDDEN, args);
    }

    public static ApiException notFound(String key, Object... args) {
        return new ApiException(key, HttpStatus.NOT_FOUND, args);
    }

    public static ApiException badRequest(String key, Object... args) {
        return new ApiException(key, HttpStatus.BAD_REQUEST, args);
    }
}
