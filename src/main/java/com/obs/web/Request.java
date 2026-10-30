package com.obs.web;

import com.obs.exception.AuthenticationException;
import com.obs.exception.ValidationException;
import com.obs.model.SessionUser;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Request {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> query;
    private final Map<String, List<String>> form;
    private final SessionUser user;
    private final String sessionToken;

    Request(HttpExchange exchange, Map<String, String> pathParams, SessionUser user, String sessionToken)
            throws IOException {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.user = user;
        this.sessionToken = sessionToken;
        this.query = parse(exchange.getRequestURI().getRawQuery());
        this.form = readForm(exchange);
    }

    /** Value from the form body, falling back to the query string. */
    public String param(String name) {
        List<String> values = form.getOrDefault(name, query.get(name));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    public List<String> params(String name) {
        List<String> values = form.getOrDefault(name, query.get(name));
        return values == null ? List.of() : values;
    }

    /** All single-valued form fields, for services that validate a whole form at once. */
    public Map<String, String> formFields() {
        Map<String, String> fields = new HashMap<>();
        form.forEach((k, v) -> fields.put(k, v.isEmpty() ? null : v.get(0)));
        return fields;
    }

    public int pathInt(String name) {
        try {
            return Integer.parseInt(pathParams.get(name));
        } catch (NumberFormatException e) {
            throw new ValidationException("'" + pathParams.get(name) + "' is not a valid id.");
        }
    }

    public int intParam(String name, int defaultValue) {
        String value = param(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("'" + name + "' must be a whole number.");
        }
    }

    /** The signed-in user. Routes registered as USER or ADMIN are guaranteed to have one. */
    public SessionUser user() {
        if (user == null) {
            throw new AuthenticationException("Please sign in to continue.");
        }
        return user;
    }

    public SessionUser userOrNull() {
        return user;
    }

    public String sessionToken() {
        return sessionToken;
    }

    public HttpExchange exchange() {
        return exchange;
    }

    private static Map<String, List<String>> readForm(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            return Map.of();
        }
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new ValidationException("Request is too large.");
            }
            return parse(new String(body, StandardCharsets.UTF_8));
        }
    }

    static Map<String, List<String>> parse(String raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return out;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Malformed request parameters.");
        }
    }
}
