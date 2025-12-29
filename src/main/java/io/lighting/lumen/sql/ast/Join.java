package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record Join(JoinType type, TableRef table, Expr on) {
    public Join {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(on, "on");
    }
}
