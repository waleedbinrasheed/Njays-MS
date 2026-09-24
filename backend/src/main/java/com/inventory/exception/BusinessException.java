package com.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * A rule violation the caller can fix (duplicate ISBN, insufficient stock, inactive record...).
 * The message is shown to the user, so it must never contain internal details.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
