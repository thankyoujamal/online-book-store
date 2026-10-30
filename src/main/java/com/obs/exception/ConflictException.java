package com.obs.exception;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public int status() {
        return 409;
    }
}
