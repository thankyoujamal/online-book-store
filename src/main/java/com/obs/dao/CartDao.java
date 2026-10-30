package com.obs.dao;

import com.obs.model.CartItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CartDao {

    private static final String SELECT = "SELECT " + BookDao.COLUMNS + ", ci.quantity"
            + " FROM cart_items ci"
            + " JOIN books b ON b.id = ci.book_id"
            + " JOIN categories c ON c.id = b.category_id"
            + " WHERE ci.user_id = ?"
            + " ORDER BY ci.added_at";

    public List<CartItem> findByUser(Connection c, int userId) throws SQLException {
        return query(c, SELECT, userId);
    }

    /** Same as findByUser but locks the cart and book rows until the surrounding transaction ends. */
    public List<CartItem> findByUserForUpdate(Connection c, int userId) throws SQLException {
        return query(c, SELECT + " FOR UPDATE", userId);
    }

    public int quantityOf(Connection c, int userId, int bookId) throws SQLException {
        String sql = "SELECT quantity FROM cart_items WHERE user_id = ? AND book_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void upsert(Connection c, int userId, int bookId, int quantity) throws SQLException {
        String sql = """
                INSERT INTO cart_items (user_id, book_id, quantity) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE quantity = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        }
    }

    public boolean remove(Connection c, int userId, int bookId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM cart_items WHERE user_id = ? AND book_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            return ps.executeUpdate() == 1;
        }
    }

    public void clear(Connection c, int userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM cart_items WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private static List<CartItem> query(Connection c, String sql, int userId) throws SQLException {
        List<CartItem> items = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new CartItem(BookDao.map(rs), rs.getInt("quantity")));
                }
            }
        }
        return items;
    }
}
