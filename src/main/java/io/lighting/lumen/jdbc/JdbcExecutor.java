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

    public <T> T executeAndReturnGeneratedKey(
        RenderedSql renderedSql,
        String columnLabel,
        GeneratedKeyMapper<T> mapper
    ) throws SQLException {
        Objects.requireNonNull(renderedSql, "renderedSql");
        Objects.requireNonNull(columnLabel, "columnLabel");
        Objects.requireNonNull(mapper, "mapper");
        Connection conn = acquireConnection();
        try (PreparedStatement statement = conn.prepareStatement(renderedSql.sql(), new String[] { columnLabel })) {
            bind(statement, renderedSql.binds());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("No generated keys returned");
                }
                return mapper.map(keys);
            }
        } finally {
            releaseConnection(conn);
        }
    }

    public <T> ResultStream<T> fetchStream(RenderedSql renderedSql, RowMapper<T> mapper, int fetchSize)
        throws SQLException {
        Objects.requireNonNull(renderedSql, "renderedSql");
        Objects.requireNonNull(mapper, "mapper");
        if (fetchSize < 1) {
            throw new IllegalArgumentException("fetchSize must be >= 1");
        }
        Connection conn = acquireConnection();
        boolean closeConnection = connection == null;
        try {
            PreparedStatement statement = conn.prepareStatement(renderedSql.sql());
            statement.setFetchSize(fetchSize);
            bind(statement, renderedSql.binds());
            ResultSet resultSet = statement.executeQuery();
            return new ResultStream<>(conn, statement, resultSet, mapper, closeConnection);
        } catch (SQLException ex) {
            if (closeConnection) {
                try {
                    conn.close();
                } catch (SQLException ignored) {
                    // ignore close failure
                }
            }
            throw ex;
        }
    }

    public int[] executeBatch(RenderedSql template, List<List<Bind>> batchBinds, int batchSize) throws SQLException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(batchBinds, "batchBinds");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        if (batchBinds.isEmpty()) {
            return new int[0];
        }
        Connection conn = acquireConnection();
        try (PreparedStatement statement = conn.prepareStatement(template.sql())) {
            List<Integer> results = new ArrayList<>();
            int counter = 0;
            for (List<Bind> binds : batchBinds) {
                bind(statement, binds);
                statement.addBatch();
                counter++;
                if (counter == batchSize) {
                    appendBatchResults(results, statement.executeBatch());
                    counter = 0;
                }
            }
            if (counter > 0) {
                appendBatchResults(results, statement.executeBatch());
            }
            return results.stream().mapToInt(Integer::intValue).toArray();
        } finally {
            releaseConnection(conn);
        }
    }

    private void appendBatchResults(List<Integer> results, int[] updates) {
        if (updates == null) {
            return;
        }
        for (int update : updates) {
            results.add(update);
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
