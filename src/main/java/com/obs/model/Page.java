package com.obs.model;

import java.util.List;

public record Page<T>(List<T> items, int total, int page, int pageSize) {

    public int totalPages() {
        return Math.max(1, (int) Math.ceil(total / (double) pageSize));
    }
}
