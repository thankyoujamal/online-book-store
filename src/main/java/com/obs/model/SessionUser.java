package com.obs.model;

public record SessionUser(int id, String fullName, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
