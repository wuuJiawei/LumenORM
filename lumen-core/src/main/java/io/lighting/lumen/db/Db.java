package io.lighting.lumen.db;

import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.jdbc.ResultStream;
import io.lighting.lumen.jdbc.GeneratedKeyMapper;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.dsl.DbDsl;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
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

    /**
     * 执行分页查询，按需执行 count 查询。
     *
     * @param pageQuery  已包含分页的查询
     * @param countQuery count 查询（当 {@link PageRequest#searchCount()} 为 true 时必须提供）
     * @param pageRequest 分页请求
     * @param mapper     行映射器
     * @param <T>        结果类型
     * @return 分页结果
     * @throws SQLException 数据库访问异常
     */
    default <T> PageResult<T> page(
        Query pageQuery,
        Query countQuery,
        PageRequest pageRequest,
        RowMapper<T> mapper
    ) throws SQLException {
        Objects.requireNonNull(pageQuery, "pageQuery");
        Objects.requireNonNull(pageRequest, "pageRequest");
        Objects.requireNonNull(mapper, "mapper");
        List<T> items = fetch(pageQuery, mapper);
        if (!pageRequest.searchCount()) {
            return new PageResult<>(items, pageRequest.page(), pageRequest.pageSize(), PageResult.TOTAL_UNKNOWN);
        }
        if (countQuery == null) {
            throw new IllegalArgumentException("countQuery is required when searchCount is true");
        }
        List<Long> totals = fetch(countQuery, rs -> rs.getLong(1));
        long total = totals.isEmpty() ? 0L : totals.get(0);
        return new PageResult<>(items, pageRequest.page(), pageRequest.pageSize(), total);
    }

    default DbDsl dsl() {
        throw new UnsupportedOperationException("Db DSL is not available for this implementation");
    }
}
