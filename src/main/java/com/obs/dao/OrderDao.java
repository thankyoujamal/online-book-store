package com.obs.dao;

import com.obs.model.Order;
import com.obs.model.OrderItem;
import com.obs.model.OrderStatus;
import com.obs.model.PaymentMethod;
import com.obs.model.PaymentStatus;
import com.obs.model.ShippingDetails;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OrderDao {

    private static final String SELECT = """
            SELECT o.id, o.user_id, u.full_name, o.status, o.payment_method, o.payment_gateway, o.payment_status,
                   o.gateway_order_id, o.payment_ref, o.paid_at, o.subtotal, o.shipping, o.total,
                   o.shipping_name, o.shipping_phone, o.shipping_address, o.created_at
            FROM orders o JOIN users u ON u.id = o.user_id
            """;

    public int insert(Connection c, int userId, PaymentMethod method, String gateway, BigDecimal subtotal,
                      BigDecimal shipping, BigDecimal total, ShippingDetails ship) throws SQLException {
        String sql = """
                INSERT INTO orders (user_id, status, payment_method, payment_gateway, subtotal, shipping, total,
                                    shipping_name, shipping_phone, shipping_address)
                VALUES (?, 'PLACED', ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, method.name());
            ps.setString(3, gateway);
            ps.setBigDecimal(4, subtotal);
            ps.setBigDecimal(5, shipping);
            ps.setBigDecimal(6, total);
            ps.setString(7, ship.name());
            ps.setString(8, ship.phone());
            ps.setString(9, ship.address());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public void insertItems(Connection c, int orderId, List<OrderItem> items) throws SQLException {
        String sql = """
                INSERT INTO order_items (order_id, book_id, title, author, unit_price, quantity)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (OrderItem item : items) {
                ps.setInt(1, orderId);
                if (item.bookId() == null) {
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setInt(2, item.bookId());
                }
                ps.setString(3, item.title());
                ps.setString(4, item.author());
                ps.setBigDecimal(5, item.unitPrice());
                ps.setInt(6, item.quantity());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public List<Order> findByUser(Connection c, int userId) throws SQLException {
        return withItems(c, query(c, SELECT + " WHERE o.user_id = ? ORDER BY o.created_at DESC, o.id DESC", userId));
    }

    public List<Order> findRecent(Connection c, int limit) throws SQLException {
        return withItems(c, query(c, SELECT + " ORDER BY o.created_at DESC, o.id DESC LIMIT ?", limit));
    }

    public Optional<Order> findById(Connection c, int id) throws SQLException {
        List<Order> orders = withItems(c, query(c, SELECT + " WHERE o.id = ? FOR UPDATE", id));
        return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
    }

    public void updateStatus(Connection c, int orderId, OrderStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    public void setGatewayOrderId(Connection c, int orderId, String gatewayOrderId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE orders SET gateway_order_id = ? WHERE id = ?")) {
            ps.setString(1, gatewayOrderId);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    /** Marks an order paid. paymentRef is the gateway payment id, or null for cash on delivery. */
    public void markPaid(Connection c, int orderId, String paymentRef) throws SQLException {
        String sql = "UPDATE orders SET payment_status = 'PAID', payment_ref = ?, paid_at = CURRENT_TIMESTAMP"
                + " WHERE id = ? AND payment_status = 'UNPAID'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, paymentRef);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    private static List<Order> query(Connection c, String sql, int param) throws SQLException {
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(new Order(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            OrderStatus.valueOf(rs.getString("status")),
                            PaymentMethod.valueOf(rs.getString("payment_method")),
                            rs.getString("payment_gateway"),
                            PaymentStatus.valueOf(rs.getString("payment_status")),
                            rs.getString("gateway_order_id"),
                            rs.getString("payment_ref"),
                            rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toLocalDateTime(),
                            rs.getBigDecimal("subtotal"),
                            rs.getBigDecimal("shipping"),
                            rs.getBigDecimal("total"),
                            rs.getString("shipping_name"),
                            rs.getString("shipping_phone"),
                            rs.getString("shipping_address"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            List.of()));
                }
            }
        }
        return orders;
    }

    /** Loads items for all orders in one query instead of one query per order. */
    private static List<Order> withItems(Connection c, List<Order> orders) throws SQLException {
        if (orders.isEmpty()) {
            return orders;
        }
        String sql = "SELECT oi.order_id, oi.book_id, oi.title, oi.author, oi.unit_price, oi.quantity, b.cover_color"
                + " FROM order_items oi LEFT JOIN books b ON b.id = oi.book_id"
                + " WHERE oi.order_id IN (" + String.join(", ", Collections.nCopies(orders.size(), "?")) + ")"
                + " ORDER BY oi.id";
        Map<Integer, List<OrderItem>> byOrder = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < orders.size(); i++) {
                ps.setInt(i + 1, orders.get(i).id());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int bookId = rs.getInt("book_id");
                    Integer nullableBookId = rs.wasNull() ? null : bookId;
                    byOrder.computeIfAbsent(rs.getInt("order_id"), k -> new ArrayList<>()).add(new OrderItem(
                            nullableBookId,
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getBigDecimal("unit_price"),
                            rs.getInt("quantity"),
                            rs.getString("cover_color")));
                }
            }
        }
        List<Order> result = new ArrayList<>(orders.size());
        for (Order order : orders) {
            result.add(order.withItems(byOrder.getOrDefault(order.id(), List.of())));
        }
        return result;
    }
}
