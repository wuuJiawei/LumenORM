package io.lighting.lumen.meta;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EntityMeta {
    private final String table;
    private final Map<String, String> fieldToColumn;
    private final Set<String> columns;
    private final IdMeta idMeta;
    private final LogicDeleteMeta logicDeleteMeta;

    public EntityMeta(String table, Map<String, String> fieldToColumn) {
        this(table, fieldToColumn, null, null);
    }

    public EntityMeta(String table, Map<String, String> fieldToColumn, IdMeta idMeta, LogicDeleteMeta logicDeleteMeta) {
        Objects.requireNonNull(table, "table");
        if (table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
        Objects.requireNonNull(fieldToColumn, "fieldToColumn");
        this.table = table;
        this.fieldToColumn = Map.copyOf(fieldToColumn);
        this.columns = Set.copyOf(new LinkedHashSet<>(fieldToColumn.values()));
        this.idMeta = idMeta;
        this.logicDeleteMeta = logicDeleteMeta;
    }

    public String table() {
        return table;
    }

    public Map<String, String> fieldToColumn() {
        return fieldToColumn;
    }

    public Set<String> columns() {
        return columns;
    }

    public java.util.Optional<IdMeta> idMeta() {
        return java.util.Optional.ofNullable(idMeta);
    }

    public java.util.Optional<LogicDeleteMeta> logicDeleteMeta() {
        return java.util.Optional.ofNullable(logicDeleteMeta);
    }

    public String columnForField(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        String column = fieldToColumn.get(fieldName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return column;
    }
}
