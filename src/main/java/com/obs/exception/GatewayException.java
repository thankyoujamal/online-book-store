package com.obs.exception;

/** The payment gateway could not be reached or rejected the request. */
public class GatewayException extends AppException {

    public GatewayException(String message) {
        super(message);
    }

    @Override
    public int status() {
        return 502;
    }
}
