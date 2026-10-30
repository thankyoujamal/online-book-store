package com.obs.model;

import java.math.BigDecimal;
import java.util.List;

public record BookQuery(
        String search,
        List<Integer> categoryIds,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStockOnly,
        Sort sort,
        int page,
        int pageSize) {

    public enum Sort {
        POPULAR("b.rating_count DESC, b.id"),
        RATING("b.rating DESC, b.rating_count DESC"),
        PRICE_LOW("b.price ASC, b.id"),
        PRICE_HIGH("b.price DESC, b.id"),
        NEWEST("b.created_at DESC, b.id DESC"),
        TITLE("b.title ASC");

        /** Fixed ORDER BY fragments, so the sort parameter can never inject SQL. */
        public final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }

        public static Sort parse(String value) {
            if (value == null || value.isBlank()) {
                return POPULAR;
            }
            for (Sort sort : values()) {
                if (sort.name().equalsIgnoreCase(value.replace('-', '_'))) {
                    return sort;
                }
            }
            return POPULAR;
        }
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
