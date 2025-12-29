package io.lighting.lumen.meta;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EntityMeta {
    private final String table;
    private final Map<String, String> fieldToColumn;
    private final Set<String> columns;

    public EntityMeta(String table, Map<String, String> fieldToColumn) {
        Objects.requireNonNull(table, "table");
        if (table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
        Objects.requireNonNull(fieldToColumn, "fieldToColumn");
        this.table = table;
        this.fieldToColumn = Map.copyOf(fieldToColumn);
        this.columns = Set.copyOf(new LinkedHashSet<>(fieldToColumn.values()));
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

    public String columnForField(String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        String column = fieldToColumn.get(fieldName);
        if (column == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return column;
    }
}
