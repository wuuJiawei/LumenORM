package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record UpdateItem(Expr.Column column, Expr value) {
    public UpdateItem {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
    }
}
