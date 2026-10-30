package com.obs.dao;

import com.obs.model.Book;
import com.obs.model.BookForm;
import com.obs.model.BookQuery;
import com.obs.model.Page;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class BookDao {

    static final String COLUMNS = """
            b.id, b.isbn, b.title, b.subtitle, b.author, b.edition, b.category_id, c.name AS category_name,
            b.description, b.price, b.list_price, b.stock, b.rating, b.rating_count, b.cover_color, b.published_year
            """;

    private static final String SELECT = "SELECT " + COLUMNS
            + " FROM books b JOIN categories c ON c.id = b.category_id";

    public Page<Book> search(Connection c, BookQuery q) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();

        if (q.search() != null) {
            String like = "%" + escapeLike(q.search()) + "%";
            where.append(" AND (b.title LIKE ? OR b.author LIKE ? OR b.isbn = ?)");
            params.add(like);
            params.add(like);
            params.add(q.search().replaceAll("[\\s-]", ""));
        }
        if (!q.categoryIds().isEmpty()) {
            where.append(" AND b.category_id IN (")
                    .append(String.join(", ", Collections.nCopies(q.categoryIds().size(), "?")))
                    .append(')');
            params.addAll(q.categoryIds());
        }
        if (q.minPrice() != null) {
            where.append(" AND b.price >= ?");
            params.add(q.minPrice());
        }
        if (q.maxPrice() != null) {
            where.append(" AND b.price <= ?");
            params.add(q.maxPrice());
        }
        if (q.inStockOnly()) {
            where.append(" AND b.stock > 0");
        }

        int total;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM books b JOIN categories c ON c.id = b.category_id" + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                total = rs.getInt(1);
            }
        }

        String sql = SELECT + where + " ORDER BY " + q.sort().orderBy + " LIMIT ? OFFSET ?";
        List<Book> books = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int next = bind(ps, params);
            ps.setInt(next++, q.pageSize());
            ps.setInt(next, q.offset());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.add(map(rs));
                }
            }
        }
        return new Page<>(books, total, q.page(), q.pageSize());
    }

    public Optional<Book> findById(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " WHERE b.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean isbnTaken(Connection c, String isbn, Integer excludeId) throws SQLException {
        String sql = "SELECT 1 FROM books WHERE isbn = ?" + (excludeId != null ? " AND id <> ?" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, isbn);
            if (excludeId != null) {
                ps.setInt(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int insert(Connection c, BookForm f) throws SQLException {
        String sql = """
                INSERT INTO books (isbn, title, subtitle, author, edition, category_id, description,
                                   price, list_price, stock, cover_color, published_year)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindForm(ps, f);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean update(Connection c, int id, BookForm f) throws SQLException {
        String sql = """
                UPDATE books SET isbn = ?, title = ?, subtitle = ?, author = ?, edition = ?, category_id = ?,
                                 description = ?, price = ?, list_price = ?, stock = ?, cover_color = ?,
                                 published_year = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int next = bindForm(ps, f);
            ps.setInt(next, id);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM books WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    /** Decrements stock only if enough copies remain; returns false when another order got there first. */
    public boolean reserveStock(Connection c, int bookId, int quantity) throws SQLException {
        String sql = "UPDATE books SET stock = stock - ? WHERE id = ? AND stock >= ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, bookId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() == 1;
        }
    }

    public void releaseStock(Connection c, int bookId, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE books SET stock = stock + ? WHERE id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        }
    }

    static Book map(ResultSet rs) throws SQLException {
        int year = rs.getInt("published_year");
        Integer publishedYear = rs.wasNull() ? null : year;
        return new Book(
                rs.getInt("id"),
                rs.getString("isbn"),
                rs.getString("title"),
                rs.getString("subtitle"),
                rs.getString("author"),
                rs.getString("edition"),
                rs.getInt("category_id"),
                rs.getString("category_name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("list_price"),
                rs.getInt("stock"),
                rs.getBigDecimal("rating"),
                rs.getInt("rating_count"),
                rs.getString("cover_color"),
                publishedYear);
    }

    private static int bindForm(PreparedStatement ps, BookForm f) throws SQLException {
        ps.setString(1, f.isbn());
        ps.setString(2, f.title());
        ps.setString(3, f.subtitle());
        ps.setString(4, f.author());
        ps.setString(5, f.edition());
        ps.setInt(6, f.categoryId());
        ps.setString(7, f.description());
        ps.setBigDecimal(8, f.price());
        ps.setBigDecimal(9, f.listPrice());
        ps.setInt(10, f.stock());
        ps.setString(11, f.coverColor());
        if (f.publishedYear() == null) {
            ps.setNull(12, Types.SMALLINT);
        } else {
            ps.setInt(12, f.publishedYear());
        }
        return 13;
    }

    private static int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int i = 1;
        for (Object p : params) {
            ps.setObject(i++, p);
        }
        return i;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
