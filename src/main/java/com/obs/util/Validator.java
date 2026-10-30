package com.obs.util;

import com.obs.exception.ValidationException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects field errors while parsing raw request strings into typed values.
 * Call {@link #throwIfInvalid()} once all fields have been read.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9 ]{10,15}$");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final Map<String, String> errors = new LinkedHashMap<>();

    public String text(String field, String value, int min, int max, boolean required) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            if (required) {
                error(field, "This field is required.");
            }
            return v.isEmpty() ? null : v;
        }
        if (v.length() < min) {
            error(field, "Must be at least " + min + " characters.");
        } else if (v.length() > max) {
            error(field, "Must be " + max + " characters or fewer.");
        }
        return v;
    }

    public String email(String field, String value) {
        String v = text(field, value, 1, 120, true);
        if (v != null && !EMAIL.matcher(v).matches()) {
            error(field, "Enter a valid email address, like name@example.com.");
        }
        return v == null ? null : v.toLowerCase();
    }

    public String phone(String field, String value) {
        String v = text(field, value, 1, 16, true);
        if (v != null && !PHONE.matcher(v).matches()) {
            error(field, "Enter a 10-digit mobile number.");
        }
        return v;
    }

    public String password(String field, String value) {
        if (value == null || value.isEmpty()) {
            error(field, "This field is required.");
            return null;
        }
        if (value.length() < 8) {
            error(field, "Use at least 8 characters.");
        } else if (value.length() > 128) {
            error(field, "Must be 128 characters or fewer.");
        } else if (!value.matches(".*[A-Za-z].*") || !value.matches(".*[0-9].*")) {
            error(field, "Include at least one letter and one number.");
        }
        return value;
    }

    public Integer integer(String field, String value, int min, int max, boolean required) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            if (required) {
                error(field, "This field is required.");
            }
            return null;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                error(field, "Must be between " + min + " and " + max + ".");
            }
            return n;
        } catch (NumberFormatException e) {
            error(field, "Must be a whole number.");
            return null;
        }
    }

    public BigDecimal money(String field, String value, boolean required) {
        String v = value == null ? "" : value.strip().replace(",", "");
        if (v.isEmpty()) {
            if (required) {
                error(field, "This field is required.");
            }
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(v);
            if (amount.signum() <= 0) {
                error(field, "Must be greater than zero.");
            } else if (amount.scale() > 2) {
                error(field, "Use at most two decimal places.");
            } else if (amount.compareTo(new BigDecimal("99999999")) > 0) {
                error(field, "That price is too large.");
            }
            return amount;
        } catch (NumberFormatException e) {
            error(field, "Must be a number, like 499 or 499.50.");
            return null;
        }
    }

    /** Accepts ISBN-10 or ISBN-13 with or without hyphens and verifies the check digit. */
    public String isbn(String field, String value) {
        String raw = text(field, value, 10, 17, true);
        if (raw == null) {
            return null;
        }
        String isbn = raw.replaceAll("[\\s-]", "").toUpperCase();
        if (!isValidIsbn(isbn)) {
            error(field, "Not a valid ISBN-10 or ISBN-13. Check the digits.");
        }
        return isbn;
    }

    public String hexColor(String field, String value) {
        String v = text(field, value, 7, 7, true);
        if (v != null && !HEX_COLOR.matcher(v).matches()) {
            error(field, "Use a hex colour like #2d5d8a.");
        }
        return v == null ? null : v.toLowerCase();
    }

    public void check(boolean condition, String field, String message) {
        if (!condition) {
            error(field, message);
        }
    }

    public void error(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }
    }

    static boolean isValidIsbn(String isbn) {
        if (isbn.matches("\\d{9}[\\dX]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                char c = isbn.charAt(i);
                int digit = c == 'X' ? 10 : c - '0';
                sum += digit * (10 - i);
            }
            return sum % 11 == 0;
        }
        if (isbn.matches("\\d{13}")) {
            int sum = 0;
            for (int i = 0; i < 13; i++) {
                int digit = isbn.charAt(i) - '0';
                sum += digit * (i % 2 == 0 ? 1 : 3);
            }
            return sum % 10 == 0;
        }
        return false;
    }
}
