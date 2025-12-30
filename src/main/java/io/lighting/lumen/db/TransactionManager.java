package io.lighting.lumen.db;

import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.template.EntityNameResolver;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public final class TransactionManager {
    private final DataSource dataSource;
    private final SqlRenderer renderer;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;
    private final EntityNameResolver entityNameResolver;
    private final List<DbObserver> observers;

    public TransactionManager(
        DataSource dataSource,
        SqlRenderer renderer,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        this(dataSource, renderer, dialect, metaRegistry, entityNameResolver, List.of());
    }

    public TransactionManager(
        DataSource dataSource,
        SqlRenderer renderer,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver,
        List<DbObserver> observers
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
        this.observers = List.copyOf(Objects.requireNonNull(observers, "observers"));
    }

    public <T> T inTransaction(TransactionCallback<T> callback) throws SQLException {
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            JdbcExecutor executor = new JdbcExecutor(connection);
            DefaultDb db = new DefaultDb(
                executor,
                renderer,
                dialect,
                metaRegistry,
                entityNameResolver,
                observers
            );
            try {
                T result = callback.apply(db);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }
}
