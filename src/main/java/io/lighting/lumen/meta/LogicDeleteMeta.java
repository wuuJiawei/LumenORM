package io.lighting.lumen.meta;

import java.util.Objects;

public record LogicDeleteMeta(String fieldName, String columnName, Object activeValue, Object deletedValue) {
    public LogicDeleteMeta {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(columnName, "columnName");
    }
}
