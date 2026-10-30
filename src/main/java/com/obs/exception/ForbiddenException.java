package com.obs.exception;

public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public int status() {
        return 403;
    }
}
