package com.obs.service;

import com.obs.exception.ValidationException;
import com.obs.util.Validator;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A self-contained payment gateway for demos: no account, no network, no real money.
 * It validates payment details the way a real gateway would, can decline on request,
 * and signs successful payments exactly like Razorpay, so the store's verification
 * path is the same for both.
 *
 * Declines: UPI ID failure@demo, card 4000 0000 0000 0002, or the "Declining Demo Bank".
 */
public final class DemoGateway implements PaymentGateway {

    public static final String DECLINE_UPI = "failure@demo";
    public static final String DECLINE_CARD = "4000000000000002";
    public static final String DECLINE_BANK = "DECLINE";

    private static final Pattern UPI_ID = Pattern.compile("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z]{2,32}$");
    private static final Pattern EXPIRY = Pattern.compile("^(0[1-9]|1[0-2])\\s*/\\s*(\\d{2})$");
    private static final Set<String> BANKS = Set.of("SBI", "HDFC", "ICICI", "AXIS", "KOTAK", DECLINE_BANK);

    private final SecureRandom random = new SecureRandom();
    /** Generated per run; only this process can produce signatures the store will accept. */
    private final String secret;

    public DemoGateway() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        this.secret = HexFormat.of().formatHex(bytes);
    }

    @Override
    public String name() {
        return "DEMO";
    }

    @Override
    public boolean isTestMode() {
        return true;
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public String createOrder(long amountPaise, String receipt) {
        return "demo_order_" + token(14);
    }

    @Override
    public boolean verifySignature(String gatewayOrderId, String paymentId, String signature) {
        return Signatures.matches(secret, gatewayOrderId, paymentId, signature);
    }

    /** The gateway's "pay" step. Returns the payment id and signature, or throws if declined or invalid. */
    public Map<String, String> charge(String gatewayOrderId, String method, Map<String, String> details) {
        if (gatewayOrderId == null || !gatewayOrderId.startsWith("demo_order_")) {
            throw new ValidationException("This payment session is invalid. Close the window and try again.");
        }
        Validator v = new Validator();
        boolean decline;
        String m = method == null ? "" : method.trim().toUpperCase();
        switch (m) {
            case "UPI" -> {
                String upi = v.text("upiId", details.get("upiId"), 1, 100, true);
                if (upi != null && !UPI_ID.matcher(upi).matches()) {
                    v.error("upiId", "Enter a UPI ID like name@okbank.");
                }
                decline = DECLINE_UPI.equalsIgnoreCase(upi);
            }
            case "CARD" -> {
                String number = details.getOrDefault("cardNumber", "").replaceAll("[\\s-]", "");
                if (!number.matches("\\d{12,19}") || !luhn(number)) {
                    v.error("cardNumber", "Enter a valid card number.");
                }
                var expiry = EXPIRY.matcher(details.getOrDefault("cardExpiry", "").trim());
                if (!expiry.matches()) {
                    v.error("cardExpiry", "Use MM/YY.");
                } else {
                    YearMonth exp = YearMonth.of(2000 + Integer.parseInt(expiry.group(2)), Integer.parseInt(expiry.group(1)));
                    v.check(!exp.isBefore(YearMonth.now()), "cardExpiry", "This card has expired.");
                }
                v.check(details.getOrDefault("cardCvv", "").trim().matches("\\d{3,4}"), "cardCvv", "3 or 4 digits.");
                v.text("cardName", details.get("cardName"), 2, 60, true);
                decline = DECLINE_CARD.equals(number);
            }
            case "NETBANKING" -> {
                String bank = details.getOrDefault("bank", "");
                v.check(BANKS.contains(bank), "bank", "Choose your bank.");
                decline = DECLINE_BANK.equals(bank);
            }
            default -> throw new ValidationException("Choose UPI, card or netbanking.");
        }
        v.throwIfInvalid();
        if (decline) {
            throw new ValidationException("Payment declined by the bank. No money was taken. Try another method.",
                    Map.of("declined", "true"));
        }

        String paymentId = "demo_pay_" + token(14);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("gatewayOrderId", gatewayOrderId);
        result.put("paymentId", paymentId);
        result.put("signature", Signatures.sign(secret, gatewayOrderId, paymentId));
        return result;
    }

    private String token(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
