package io.lighting.lumen.meta;

import java.util.Objects;

public record IdMeta(String fieldName, String columnName, IdStrategy strategy) {
    public IdMeta {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(columnName, "columnName");
        Objects.requireNonNull(strategy, "strategy");
    }
}
