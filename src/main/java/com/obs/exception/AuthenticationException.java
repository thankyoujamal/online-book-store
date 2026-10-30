package com.obs.exception;

public class AuthenticationException extends AppException {

    public AuthenticationException(String message) {
        super(message);
    }

    @Override
    public int status() {
        return 401;
    }
}
