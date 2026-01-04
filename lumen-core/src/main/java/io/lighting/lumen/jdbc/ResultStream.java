package io.lighting.lumen.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public final class ResultStream<T> implements AutoCloseable {
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private final RowMapper<T> mapper;
    private final boolean closeConnection;
    private boolean closed;
    private boolean hasRow;

    ResultStream(
        Connection connection,
        PreparedStatement statement,
        ResultSet resultSet,
        RowMapper<T> mapper,
        boolean closeConnection
    ) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.statement = Objects.requireNonNull(statement, "statement");
        this.resultSet = Objects.requireNonNull(resultSet, "resultSet");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.closeConnection = closeConnection;
    }

    public boolean next() throws SQLException {
        if (closed) {
            return false;
        }
        hasRow = resultSet.next();
        if (!hasRow) {
            close();
        }
        return hasRow;
    }

    public T row() throws SQLException {
        if (!hasRow) {
            throw new IllegalStateException("Call next() before row()");
        }
        return mapper.map(resultSet);
    }

    public T nextRow() throws SQLException {
        if (!next()) {
            return null;
        }
        return row();
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        SQLException error = null;
        try {
            resultSet.close();
        } catch (SQLException ex) {
            error = ex;
        }
        try {
            statement.close();
        } catch (SQLException ex) {
            if (error == null) {
                error = ex;
            }
        }
        if (closeConnection) {
            try {
                connection.close();
            } catch (SQLException ex) {
                if (error == null) {
                    error = ex;
                }
            }
        }
        if (error != null) {
            throw error;
        }
    }
}
