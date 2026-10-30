package com.obs.model;

import java.math.BigDecimal;

/** Title, author and price are copied at purchase time, so history survives later edits or deletion of the book. */
public record OrderItem(Integer bookId, String title, String author, BigDecimal unitPrice, int quantity,
                        String coverColor) {
}
