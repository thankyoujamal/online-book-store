package com.obs.model;

import java.math.BigDecimal;

public record Book(
        int id,
        String isbn,
        String title,
        String subtitle,
        String author,
        String edition,
        int categoryId,
        String categoryName,
        String description,
        BigDecimal price,
        BigDecimal listPrice,
        int stock,
        BigDecimal rating,
        int ratingCount,
        String coverColor,
        Integer publishedYear) {
}
