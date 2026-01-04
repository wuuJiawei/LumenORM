package io.lighting.lumen.sql.ast;

import java.util.Objects;

public record DeleteStmt(
    TableRef table,
    Expr where
) implements Stmt {
    public DeleteStmt {
        Objects.requireNonNull(table, "table");
    }
}
