package io.lighting.lumen.db;

import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.jdbc.ResultStream;
import io.lighting.lumen.jdbc.GeneratedKeyMapper;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.sql.BatchSql;
import io.lighting.lumen.sql.Bindings;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public interface Db {
    <T> List<T> fetch(Query query, RowMapper<T> mapper) throws SQLException;

    default <T> List<T> fetch(Query query, Class<T> type) throws SQLException {
        Objects.requireNonNull(type, "type");
        return fetch(query, RowMappers.auto(type));
    }

    int execute(Command command) throws SQLException;

    default int executeOptimistic(Command command) throws SQLException {
        int updated = execute(command);
        if (updated != 1) {
            throw new OptimisticLockException("Optimistic lock expected 1 row but got " + updated);
        }
        return updated;
    }

    int[] executeBatch(BatchSql batchSql) throws SQLException;

    <T> ResultStream<T> fetchStream(Query query, RowMapper<T> mapper, int fetchSize) throws SQLException;

    <T> List<T> run(String sqlText, Bindings bindings, RowMapper<T> mapper) throws SQLException;

    <T> T executeAndReturnGeneratedKey(Command command, String columnLabel, GeneratedKeyMapper<T> mapper)
        throws SQLException;
}
