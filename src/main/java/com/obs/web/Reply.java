package com.obs.web;

import java.util.List;

public record Reply(int status, Object body, List<String> cookies) {

    public static Reply ok(Object body) {
        return new Reply(200, body, List.of());
    }

    public static Reply created(Object body) {
        return new Reply(201, body, List.of());
    }

    public static Reply noContent() {
        return new Reply(204, null, List.of());
    }

    public Reply withCookie(String cookie) {
        return new Reply(status, body, List.of(cookie));
    }
}
