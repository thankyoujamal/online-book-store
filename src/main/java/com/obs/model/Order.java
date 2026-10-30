package com.obs.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Order(
        int id,
        int userId,
        String customerName,
        OrderStatus status,
        PaymentMethod paymentMethod,
        String paymentGateway,
        PaymentStatus paymentStatus,
        String gatewayOrderId,
        String paymentRef,
        LocalDateTime paidAt,
        BigDecimal subtotal,
        BigDecimal shipping,
        BigDecimal total,
        String shippingName,
        String shippingPhone,
        String shippingAddress,
        LocalDateTime createdAt,
        List<OrderItem> items) {

    public boolean awaitingOnlinePayment() {
        return paymentMethod == PaymentMethod.ONLINE && paymentStatus == PaymentStatus.UNPAID
                && status == OrderStatus.PLACED;
    }

    public Order withItems(List<OrderItem> newItems) {
        return new Order(id, userId, customerName, status, paymentMethod, paymentGateway, paymentStatus, gatewayOrderId,
                paymentRef, paidAt, subtotal, shipping, total, shippingName, shippingPhone, shippingAddress,
                createdAt, newItems);
    }
}
