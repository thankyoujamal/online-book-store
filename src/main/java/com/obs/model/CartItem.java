package com.obs.model;

import java.math.BigDecimal;

public record CartItem(Book book, int quantity) {

    public BigDecimal lineTotal() {
        return book.price().multiply(BigDecimal.valueOf(quantity));
    }
}
