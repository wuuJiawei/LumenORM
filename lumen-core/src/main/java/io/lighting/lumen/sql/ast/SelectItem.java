package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record SelectItem(Expr expr, String alias) {
    public SelectItem {
        Objects.requireNonNull(expr, "expr");
    }
}
