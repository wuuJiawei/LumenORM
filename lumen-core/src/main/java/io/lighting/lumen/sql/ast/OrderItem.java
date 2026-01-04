package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record OrderItem(Expr expr, boolean asc) {
    public OrderItem {
        Objects.requireNonNull(expr, "expr");
    }
}
