package com.obs.model;

import java.math.BigDecimal;
import java.util.List;

public record Cart(List<CartItem> items, int itemCount, BigDecimal subtotal, BigDecimal shipping, BigDecimal total,
                   BigDecimal freeShippingThreshold) {
}
