package com.obs.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection connection = getConnection()) {
            return work.run(connection);
        }
    }

    /**
     * Runs the work in a single transaction. Any exception, checked or not,
     * rolls the transaction back before being rethrown.
     */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Fails fast at startup instead of on the first request. */
    public void verify() throws SQLException {
        try (Connection connection = getConnection()) {
            if (!connection.isValid(3)) {
                throw new SQLException("Database connection is not valid");
            }
        }
    }
}
