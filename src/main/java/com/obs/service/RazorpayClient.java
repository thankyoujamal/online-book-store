package com.obs.service;

import com.obs.exception.GatewayException;
import com.obs.util.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two Razorpay calls the store needs, using only the JDK:
 * creating a gateway order, and verifying the signature returned by Checkout.
 * Works the same with test keys (rzp_test_...) and live keys.
 */
public final class RazorpayClient implements PaymentGateway {

    private static final Logger LOG = Logger.getLogger(RazorpayClient.class.getName());
    private static final URI ORDERS_URL = URI.create("https://api.razorpay.com/v1/orders");
    // Razorpay's order response is flat JSON; the top-level "id" is the first one in the body.
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DESCRIPTION = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");

    private final String keyId;
    private final String keySecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayClient(String keyId, String keySecret) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
    }

    public boolean isConfigured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public boolean isTestMode() {
        return keyId.startsWith("rzp_test_");
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    /** Creates a Razorpay order and returns its id (order_...). Amount is in paise. */
    @Override
    public String createOrder(long amountPaise, String receipt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", "INR");
        body.put("receipt", receipt);

        String auth = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(ORDERS_URL)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Razorpay unreachable", e);
            throw new GatewayException("Can't reach the payment gateway right now. Try again in a moment.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Payment request was interrupted. Please try again.");
        }

        if (response.statusCode() / 100 != 2) {
            String reason = find(DESCRIPTION, response.body());
            LOG.warning("Razorpay order creation failed (" + response.statusCode() + "): " + response.body());
            if (response.statusCode() == 401) {
                throw new GatewayException("Payment gateway rejected the store's API keys. Check payment.razorpay.* in app.properties.");
            }
            throw new GatewayException("Payment gateway error: " + (reason != null ? reason : "HTTP " + response.statusCode()));
        }
        String id = find(ID, response.body());
        if (id == null) {
            throw new GatewayException("Payment gateway sent an unexpected response.");
        }
        return id;
    }

    /** Checkout's handler returns a signature of "order_id|payment_id", HMAC-SHA256'd with the key secret. */
    @Override
    public boolean verifySignature(String gatewayOrderId, String paymentId, String signature) {
        return Signatures.matches(keySecret, gatewayOrderId, paymentId, signature);
    }

    private static String find(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : null;
    }
}
