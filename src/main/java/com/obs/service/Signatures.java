package com.obs.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Razorpay-style payment signatures: hex(HMAC-SHA256(secret, "orderId|paymentId")). */
final class Signatures {

    private Signatures() {
    }

    static String sign(String secret, String gatewayOrderId, String paymentId) {
        return HexFormat.of().formatHex(hmac(secret, gatewayOrderId, paymentId));
    }

    static boolean matches(String secret, String gatewayOrderId, String paymentId, String signature) {
        if (gatewayOrderId == null || paymentId == null || signature == null) {
            return false;
        }
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(secret, gatewayOrderId, paymentId), actual);
    }

    private static byte[] hmac(String secret, String gatewayOrderId, String paymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal((gatewayOrderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", e);
        }
    }
}
