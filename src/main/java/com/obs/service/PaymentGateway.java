package com.obs.service;

/**
 * A payment provider. Both implementations follow the same contract as Razorpay:
 * the store creates a gateway order, the customer pays it, and the gateway returns
 * a payment id plus an HMAC signature of "gatewayOrderId|paymentId" that only the
 * store's secret can verify.
 */
public interface PaymentGateway {

    /** Stored on each online order, e.g. "DEMO" or "RAZORPAY". */
    String name();

    /** True when no real money moves. */
    boolean isTestMode();

    /** Public key the browser needs to open checkout, or null if the gateway has none. */
    String publicKey();

    /** Creates a gateway order for the amount in paise and returns its id. */
    String createOrder(long amountPaise, String receipt);

    boolean verifySignature(String gatewayOrderId, String paymentId, String signature);
}
