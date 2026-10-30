package com.obs.model;

import java.math.BigDecimal;

/** Validated input for creating or updating a book from the admin panel. */
public record BookForm(
        String isbn,
        String title,
        String subtitle,
        String author,
        String edition,
        int categoryId,
        String description,
        BigDecimal price,
        BigDecimal listPrice,
        int stock,
        String coverColor,
        Integer publishedYear) {
}
