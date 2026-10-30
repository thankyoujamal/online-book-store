package com.obs.exception;

import java.util.Map;

/** Thrown when input fails validation. Carries per-field messages so forms can show them inline. */
public class ValidationException extends AppException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    @Override
    public int status() {
        return 400;
    }
}
