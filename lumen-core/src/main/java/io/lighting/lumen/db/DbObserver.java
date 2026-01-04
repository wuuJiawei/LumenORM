package io.lighting.lumen.db;

import io.lighting.lumen.sql.RenderedSql;

public interface DbObserver {
    default void beforeRender(DbOperation operation, Object source) {
    }

    default void afterRender(DbOperation operation, Object source, RenderedSql rendered, long elapsedNanos) {
    }

    default void onRenderError(DbOperation operation, Object source, Exception error, long elapsedNanos) {
    }

    default void beforeExecute(DbOperation operation, Object source, RenderedSql rendered) {
    }

    default void afterExecute(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long elapsedNanos,
        int rowCount
    ) {
    }

    default void onExecuteError(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long elapsedNanos,
        Exception error
    ) {
    }
}
