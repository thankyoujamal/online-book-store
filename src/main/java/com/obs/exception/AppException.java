package com.obs.exception;

/**
 * Base type for errors that are safe to show to the user. Each subclass maps to an HTTP status.
 */
public abstract class AppException extends RuntimeException {

    protected AppException(String message) {
        super(message);
    }

    public abstract int status();
}
