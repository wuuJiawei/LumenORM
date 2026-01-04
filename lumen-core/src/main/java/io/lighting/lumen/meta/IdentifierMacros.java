package io.lighting.lumen.meta;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.TableRef;
import java.util.Objects;

public final class IdentifierMacros {
    private final EntityMetaRegistry registry;

    public IdentifierMacros(EntityMetaRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public EntityMeta metaOf(Class<?> entityType) {
        return registry.metaOf(entityType);
    }

    public String table(Class<?> entityType) {
        return registry.metaOf(entityType).table();
    }

    public String column(Class<?> entityType, String fieldName) {
        return registry.metaOf(entityType).columnForField(fieldName);
    }

    public TableRef tableRef(Class<?> entityType, String alias) {
        return new TableRef(table(entityType), alias);
    }

    public Expr.Column columnRef(Class<?> entityType, String tableAlias, String fieldName) {
        return new Expr.Column(tableAlias, column(entityType, fieldName));
    }
}
