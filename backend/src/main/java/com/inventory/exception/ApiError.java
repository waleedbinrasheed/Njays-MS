package com.inventory.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by every API failure.
 * {@code errors} is only present for field-level validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String message,
        String path,
        List<FieldError> errors
) {
    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String message, String path) {
        return new ApiError(Instant.now(), status, message, path, null);
    }

    public static ApiError of(int status, String message, String path, List<FieldError> errors) {
        return new ApiError(Instant.now(), status, message, path, errors);
    }
}
