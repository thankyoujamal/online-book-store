package com.obs.dao;

import com.obs.model.Category;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CategoryDao {

    public List<Category> findAllWithCounts(Connection c) throws SQLException {
        String sql = """
                SELECT c.id, c.name, c.slug, COUNT(b.id) AS book_count
                FROM categories c
                LEFT JOIN books b ON b.category_id = c.id
                GROUP BY c.id, c.name, c.slug
                ORDER BY c.sort_order, c.name
                """;
        List<Category> categories = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(new Category(
                        rs.getInt("id"), rs.getString("name"), rs.getString("slug"), rs.getInt("book_count")));
            }
        }
        return categories;
    }

    public boolean exists(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM categories WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
