package io.lighting.lumen.db;

import io.lighting.lumen.jdbc.JdbcExecutor;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.meta.EntityMetaRegistry;
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

public final class DefaultDb implements Db {
    private final JdbcExecutor executor;
    private final SqlRenderer renderer;
    private final Dialect dialect;
    private final EntityMetaRegistry metaRegistry;
    private final EntityNameResolver entityNameResolver;

    public DefaultDb(
        JdbcExecutor executor,
        SqlRenderer renderer,
        Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.metaRegistry = Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.entityNameResolver = Objects.requireNonNull(entityNameResolver, "entityNameResolver");
    }

    @Override
    public <T> List<T> fetch(Query query, RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(query, "query");
        RenderedSql rendered = query.render(renderer);
        return executor.fetch(rendered, mapper);
    }

    @Override
    public int execute(Command command) throws SQLException {
        Objects.requireNonNull(command, "command");
        RenderedSql rendered = command.render(renderer);
        return executor.execute(rendered);
    }

    @Override
    public <T> List<T> run(String sqlText, Bindings bindings, RowMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(sqlText, "sqlText");
        Objects.requireNonNull(bindings, "bindings");
        SqlTemplate template = SqlTemplate.parse(sqlText);
        TemplateContext context = new TemplateContext(
            bindings.asMap(),
            dialect,
            metaRegistry,
            entityNameResolver
        );
        RenderedSql rendered = template.render(context);
        return executor.fetch(rendered, mapper);
    }
}
