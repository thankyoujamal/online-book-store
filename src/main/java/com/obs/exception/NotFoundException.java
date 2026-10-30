package com.obs.exception;

public class NotFoundException extends AppException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public int status() {
        return 404;
    }
}
