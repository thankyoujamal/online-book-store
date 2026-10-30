package com.obs.web;

import com.obs.model.SessionUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sessions keyed by a random token kept in an HttpOnly cookie. Sessions slide on use. */
public final class SessionStore {

    public static final String COOKIE = "OBS_SESSION";

    private record Session(SessionUser user, Instant expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public SessionStore(Duration ttl) {
        this.ttl = ttl;
    }

    public String create(SessionUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, Instant.now().plus(ttl)));
        if (sessions.size() % 100 == 0) {
            purgeExpired();
        }
        return token;
    }

    public SessionUser find(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        sessions.put(token, new Session(session.user(), Instant.now().plus(ttl)));
        return session.user();
    }

    public void destroy(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public String cookieFor(String token) {
        return COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + ttl.toSeconds();
    }

    public String clearingCookie() {
        return COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
