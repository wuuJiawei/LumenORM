package io.lighting.lumen.sql.ast;

import java.util.List;
import java.util.Objects;

public record UpdateStmt(
    TableRef table,
    List<UpdateItem> assignments,
    Expr where
) implements Stmt {
    public UpdateStmt {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(assignments, "assignments");
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Update assignments must not be empty");
        }
        assignments = List.copyOf(assignments);
    }
}
