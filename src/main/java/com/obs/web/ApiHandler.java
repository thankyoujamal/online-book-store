package com.obs.web;

import com.obs.exception.AppException;
import com.obs.exception.ValidationException;
import com.obs.model.SessionUser;
import com.obs.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for /api/*. Resolves the route and session, enforces access, and turns
 * every exception into a JSON error with the right status. Internal details are logged,
 * never sent to the client.
 */
public final class ApiHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());

    private final Router router;
    private final SessionStore sessions;

    public ApiHandler(Router router, SessionStore sessions) {
        this.router = router;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            Router.Match match = router.match(method, path);
            if (match == null) {
                send(exchange, error(404, "No API endpoint at " + path + ".", null));
                return;
            }
            String token = readCookie(exchange, SessionStore.COOKIE);
            SessionUser user = sessions.find(token);
            Access access = match.route().access();
            if (access != Access.PUBLIC && user == null) {
                send(exchange, error(401, "Please sign in to continue.", null));
                return;
            }
            if (access == Access.ADMIN && !user.isAdmin()) {
                send(exchange, error(403, "This area is for administrators only.", null));
                return;
            }
            Request request = new Request(exchange, match.pathParams(), user, token);
            Object result = match.route().handler().handle(request);
            send(exchange, result instanceof Reply reply ? reply : Reply.ok(result));
        } catch (Router.MethodNotAllowed e) {
            send(exchange, error(405, method + " is not supported on " + path + ".", null));
        } catch (ValidationException e) {
            send(exchange, error(400, e.getMessage(), e.fieldErrors()));
        } catch (AppException e) {
            send(exchange, error(e.status(), e.getMessage(), null));
        } catch (SQLIntegrityConstraintViolationException e) {
            LOG.log(Level.WARNING, "Constraint violation on " + method + " " + path, e);
            send(exchange, error(409, "That change conflicts with existing data. Refresh and try again.", null));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Database error on " + method + " " + path, e);
            send(exchange, error(503, "The store can't reach its database right now. Please try again shortly.", null));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error on " + method + " " + path, e);
            send(exchange, error(500, "Something broke on our side. The error has been logged.", null));
        } finally {
            exchange.close();
        }
    }

    private static Reply error(int status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        if (fields != null && !fields.isEmpty()) {
            body.put("fields", fields);
        }
        return new Reply(status, body, List.of());
    }

    private static void send(HttpExchange exchange, Reply reply) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        for (String cookie : reply.cookies()) {
            headers.add("Set-Cookie", cookie);
        }
        if (reply.status() == 204 || reply.body() == null) {
            exchange.sendResponseHeaders(reply.status() == 200 ? 204 : reply.status(), -1);
            return;
        }
        byte[] bytes = Json.write(reply.body()).getBytes(StandardCharsets.UTF_8);
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readCookie(HttpExchange exchange, String name) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
