package com.obs.model;

/** Full user row, including the password hash. Never serialised to the client; see {@link SessionUser}. */
public record User(int id, String fullName, String email, String passwordHash, Role role) {

    public SessionUser toSessionUser() {
        return new SessionUser(id, fullName, email, role);
    }
}
