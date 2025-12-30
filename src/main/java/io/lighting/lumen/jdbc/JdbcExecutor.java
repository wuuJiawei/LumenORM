package io.lighting.lumen.jdbc;

import io.lighting.lumen.sql.Bind;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public final class JdbcExecutor {
    private final DataSource dataSource;
    private final Connection connection;

    public JdbcExecutor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.connection = null;
    }

    public JdbcExecutor(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.dataSource = null;
    }

    public <T> List<T> fetch(RenderedSql renderedSql, RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(renderedSql, "renderedSql");
        Objects.requireNonNull(mapper, "mapper");
        Connection conn = acquireConnection();
        try (PreparedStatement statement = conn.prepareStatement(renderedSql.sql())) {
            bind(statement, renderedSql.binds());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(resultSet));
                }
                return results;
            }
        } finally {
            releaseConnection(conn);
        }
    }

    public int execute(RenderedSql renderedSql) throws SQLException {
        Objects.requireNonNull(renderedSql, "renderedSql");
        Connection conn = acquireConnection();
        try (PreparedStatement statement = conn.prepareStatement(renderedSql.sql())) {
            bind(statement, renderedSql.binds());
            return statement.executeUpdate();
        } finally {
            releaseConnection(conn);
        }
    }

    private Connection acquireConnection() throws SQLException {
        if (connection != null) {
            return connection;
        }
        return dataSource.getConnection();
    }

    private void releaseConnection(Connection conn) throws SQLException {
        if (connection == null && conn != null) {
            conn.close();
        }
    }

    private void bind(PreparedStatement statement, List<Bind> binds) throws SQLException {
        for (int i = 0; i < binds.size(); i++) {
            int index = i + 1;
            Bind bind = binds.get(i);
            if (bind instanceof Bind.Value value) {
                if (value.jdbcType() == 0) {
                    statement.setObject(index, value.value());
                } else {
                    statement.setObject(index, value.value(), value.jdbcType());
                }
            } else if (bind instanceof Bind.NullValue nullValue) {
                int jdbcType = nullValue.jdbcType();
                if (jdbcType == 0) {
                    statement.setNull(index, Types.NULL);
                } else {
                    statement.setNull(index, jdbcType);
                }
            } else {
                throw new IllegalArgumentException("Unsupported bind: " + bind.getClass().getSimpleName());
            }
        }
    }
}
