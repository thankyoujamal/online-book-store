package com.obs.web.controller;

import com.obs.model.SessionUser;
import com.obs.service.AuthService;
import com.obs.web.Access;
import com.obs.web.Reply;
import com.obs.web.Request;
import com.obs.web.Router;
import com.obs.web.SessionStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AuthController {

    private final AuthService auth;
    private final SessionStore sessions;

    public AuthController(AuthService auth, SessionStore sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    public void register(Router router) {
        router.post("/api/auth/register", Access.PUBLIC, this::signUp);
        router.post("/api/auth/login", Access.PUBLIC, this::signIn);
        router.post("/api/auth/logout", Access.PUBLIC, this::signOut);
        router.get("/api/auth/me", Access.PUBLIC, this::me);
    }

    private Object signUp(Request req) throws Exception {
        SessionUser user = auth.register(
                req.param("fullName"), req.param("email"), req.param("password"), req.param("confirmPassword"));
        return startSession(req, user, 201);
    }

    private Object signIn(Request req) throws Exception {
        SessionUser user = auth.login(req.param("email"), req.param("password"));
        return startSession(req, user, 200);
    }

    private Object signOut(Request req) {
        sessions.destroy(req.sessionToken());
        return Reply.noContent().withCookie(sessions.clearingCookie());
    }

    private Object me(Request req) {
        Map<String, Object> body = new HashMap<>();
        body.put("user", req.userOrNull());
        return body;
    }

    private Reply startSession(Request req, SessionUser user, int status) {
        // Rotate the token on every sign-in so an old cookie can't be reused.
        sessions.destroy(req.sessionToken());
        String token = sessions.create(user);
        return new Reply(status, Map.of("user", user), List.of(sessions.cookieFor(token)));
    }
}
