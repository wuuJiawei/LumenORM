package io.lighting.lumen.sql.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record InsertStmt(
    TableRef table,
    List<String> columns,
    List<List<Expr>> rows
) implements Stmt {
    public InsertStmt {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(columns, "columns");
        Objects.requireNonNull(rows, "rows");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Insert columns must not be empty");
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Insert rows must not be empty");
        }
        List<List<Expr>> normalizedRows = new ArrayList<>(rows.size());
        for (List<Expr> row : rows) {
            Objects.requireNonNull(row, "row");
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("Insert row size must match columns");
            }
            normalizedRows.add(List.copyOf(row));
        }
        columns = List.copyOf(columns);
        rows = List.copyOf(normalizedRows);
    }
}
