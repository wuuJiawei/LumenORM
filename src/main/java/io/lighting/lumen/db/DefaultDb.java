package io.lighting.lumen.db;

import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.jdbc.ResultStream;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.BatchSql;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.template.EntityNameResolver;
import io.lighting.lumen.template.SqlTemplate;
import io.lighting.lumen.template.TemplateContext;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class DefaultDb implements Db {
    private final JdbcExecutor executor;
    private final SqlRenderer renderer;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;
    private final EntityNameResolver entityNameResolver;
    private final List<DbObserver> observers;

    public DefaultDb(
        JdbcExecutor executor,
        SqlRenderer renderer,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        this(executor, renderer, dialect, metaRegistry, entityNameResolver, List.of());
    }

    public DefaultDb(
        JdbcExecutor executor,
        SqlRenderer renderer,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver,
        List<DbObserver> observers
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
        this.observers = List.copyOf(Objects.requireNonNull(observers, "observers"));
    }

    @Override
    public <T> List<T> fetch(Query query, RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(query, "query");
        RenderedSql rendered = renderWithObservers(DbOperation.QUERY, query, () -> query.render(renderer));
        return executeFetch(DbOperation.QUERY, query, rendered, mapper);
    }

    @Override
    public int execute(Command command) throws SQLException {
        Objects.requireNonNull(command, "command");
        RenderedSql rendered = renderWithObservers(DbOperation.COMMAND, command, () -> command.render(renderer));
        return executeCommand(DbOperation.COMMAND, command, rendered);
    }

    @Override
    public int[] executeBatch(BatchSql batchSql) throws SQLException {
        Objects.requireNonNull(batchSql, "batchSql");
        RenderedSql template = batchSql.template();
        notifyBeforeExecute(DbOperation.COMMAND, batchSql, template);
        long start = System.nanoTime();
        try {
            int[] results = executor.executeBatch(
                template,
                batchSql.batches(),
                batchSql.batchSize()
            );
            notifyAfterExecute(DbOperation.COMMAND, batchSql, template, start, results.length);
            return results;
        } catch (SQLException ex) {
            notifyExecuteError(DbOperation.COMMAND, batchSql, template, start, ex);
            throw ex;
        }
    }

    @Override
    public <T> ResultStream<T> fetchStream(Query query, RowMapper<T> mapper, int fetchSize) throws SQLException {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(mapper, "mapper");
        RenderedSql rendered = renderWithObservers(DbOperation.QUERY, query, () -> query.render(renderer));
        notifyBeforeExecute(DbOperation.QUERY, query, rendered);
        long start = System.nanoTime();
        try {
            ResultStream<T> stream = executor.fetchStream(rendered, mapper, fetchSize);
            notifyAfterExecute(DbOperation.QUERY, query, rendered, start, 0);
            return stream;
        } catch (SQLException ex) {
            notifyExecuteError(DbOperation.QUERY, query, rendered, start, ex);
            throw ex;
        }
    }

    @Override
    public <T> List<T> run(String sqlText, Bindings bindings, RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(sqlText, "sqlText");
        Objects.requireNonNull(bindings, "bindings");
        RenderedSql rendered = renderWithObservers(
            DbOperation.TEMPLATE,
            sqlText,
            () -> {
                SqlTemplate template = SqlTemplate.parse(sqlText);
                TemplateContext context = new TemplateContext(
                    bindings.asMap(),
                    dialect,
                    metaRegistry,
                    entityNameResolver
                );
                return template.render(context);
            }
        );
        return executeFetch(DbOperation.TEMPLATE, sqlText, rendered, mapper);
    }

    private <T> List<T> executeFetch(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        RowMapper<T> mapper
    ) throws SQLException {
        notifyBeforeExecute(operation, source, rendered);
        long start = System.nanoTime();
        try {
            List<T> results = executor.fetch(rendered, mapper);
            notifyAfterExecute(operation, source, rendered, start, results.size());
            return results;
        } catch (SQLException ex) {
            notifyExecuteError(operation, source, rendered, start, ex);
            throw ex;
        }
    }

    private int executeCommand(DbOperation operation, Object source, RenderedSql rendered) throws SQLException {
        notifyBeforeExecute(operation, source, rendered);
        long start = System.nanoTime();
        try {
            int updated = executor.execute(rendered);
            notifyAfterExecute(operation, source, rendered, start, updated);
            return updated;
        } catch (SQLException ex) {
            notifyExecuteError(operation, source, rendered, start, ex);
            throw ex;
        }
    }

    private RenderedSql renderWithObservers(
        DbOperation operation,
        Object source,
        Supplier<RenderedSql> renderAction
    ) {
        notifyBeforeRender(operation, source);
        long start = System.nanoTime();
        try {
            RenderedSql rendered = renderAction.get();
            notifyAfterRender(operation, source, rendered, start);
            return rendered;
        } catch (RuntimeException ex) {
            notifyRenderError(operation, source, ex, start);
            throw ex;
        }
    }

    private void notifyBeforeRender(DbOperation operation, Object source) {
        for (DbObserver observer : observers) {
            observer.beforeRender(operation, source);
        }
    }

    private void notifyAfterRender(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long start
    ) {
        long elapsed = System.nanoTime() - start;
        for (DbObserver observer : observers) {
            observer.afterRender(operation, source, rendered, elapsed);
        }
    }

    private void notifyRenderError(
        DbOperation operation,
        Object source,
        Exception error,
        long start
    ) {
        long elapsed = System.nanoTime() - start;
        for (DbObserver observer : observers) {
            observer.onRenderError(operation, source, error, elapsed);
        }
    }

    private void notifyBeforeExecute(DbOperation operation, Object source, RenderedSql rendered) {
        for (DbObserver observer : observers) {
            observer.beforeExecute(operation, source, rendered);
        }
    }

    private void notifyAfterExecute(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long start,
        int rowCount
    ) {
        long elapsed = System.nanoTime() - start;
        for (DbObserver observer : observers) {
            observer.afterExecute(operation, source, rendered, elapsed, rowCount);
        }
    }

    private void notifyExecuteError(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long start,
        Exception error
    ) {
        long elapsed = System.nanoTime() - start;
        for (DbObserver observer : observers) {
            observer.onExecuteError(operation, source, rendered, elapsed, error);
        }
    }
}
