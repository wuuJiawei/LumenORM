package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record TableRef(String tableName, String alias) {
    public TableRef {
        Objects.requireNonNull(tableName, "tableName");
        if (tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
    }
}
