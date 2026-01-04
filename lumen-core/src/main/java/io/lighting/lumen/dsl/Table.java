package io.lighting.lumen.dsl;

import io.lighting.lumen.meta.IdentifierMacros;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.TableRef;
import java.util.Objects;

public final class Table {
    private final Class<?> entityType;
    private final IdentifierMacros macros;
    private final String alias;
    private final String tableName;

    Table(Class<?> entityType, IdentifierMacros macros, String alias) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.macros = Objects.requireNonNull(macros, "macros");
        this.alias = alias;
        this.tableName = macros.table(entityType);
    }

    public Table as(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        return new Table(entityType, macros, alias);
    }

    public String name() {
        return tableName;
    }

    public String alias() {
        return alias;
    }

    public TableRef ref() {
        return new TableRef(tableName, alias);
    }

    public ColumnRef col(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        String column = macros.column(entityType, fieldName);
        return new ColumnRef(alias, column);
    }

    public <T> ColumnRef col(PropertyRef<T, ?> ref) {
        LambdaProperty.Property property = LambdaProperty.resolve(ref);
        if (!property.owner().isAssignableFrom(entityType) && !entityType.isAssignableFrom(property.owner())) {
            throw new IllegalArgumentException(
                "Lambda target type mismatch: expected " + entityType.getName() + " but got " + property.owner().getName()
            );
        }
        return col(property.name());
    }

    public LogicalDelete logicalDelete() {
        io.lighting.lumen.meta.LogicDeleteMeta meta = macros.metaOf(entityType)
            .logicDeleteMeta()
            .orElseThrow(() -> new IllegalArgumentException("No @LogicDelete on " + entityType.getName()));
        ColumnRef column = new ColumnRef(alias, meta.columnName());
        return new LogicalDelete(column, meta.activeValue(), meta.deletedValue());
    }

    public Expr notDeleted() {
        return logicalDelete().isActive();
    }
}
