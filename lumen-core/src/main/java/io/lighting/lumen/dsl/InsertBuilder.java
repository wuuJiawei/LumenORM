package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InsertBuilder {
    private final TableRef table;
    private final Table tableInfo;
    private final List<String> columns = new ArrayList<>();
    private final List<List<Expr>> rows = new ArrayList<>();

    InsertBuilder(Table table) {
        this.tableInfo = Objects.requireNonNull(table, "table");
        this.table = table.ref();
    }

    public InsertBuilder columns(String... names) {
        Objects.requireNonNull(names, "names");
        columns.clear();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Column name must not be blank");
            }
            columns.add(name);
        }
        return this;
    }

    public InsertBuilder columns(ColumnRef... refs) {
        Objects.requireNonNull(refs, "refs");
        columns.clear();
        for (ColumnRef ref : refs) {
            Objects.requireNonNull(ref, "ref");
            columns.add(ref.expr().columnName());
        }
        return this;
    }

    public <T> InsertBuilder columns(PropertyRef<T, ?>... refs) {
        Objects.requireNonNull(refs, "refs");
        columns.clear();
        for (PropertyRef<T, ?> ref : refs) {
            Objects.requireNonNull(ref, "ref");
            columns.add(tableInfo.col(ref).expr().columnName());
        }
        return this;
    }

    public InsertBuilder row(Object... values) {
        Objects.requireNonNull(values, "values");
        ensureColumns();
        List<Expr> row = new ArrayList<>(values.length);
        for (Object value : values) {
            row.add(toExpr(value));
        }
        rows.add(row);
        return this;
    }

    public InsertBuilder row(Expr... values) {
        Objects.requireNonNull(values, "values");
        ensureColumns();
        rows.add(List.of(values));
        return this;
    }

    public InsertStmt build() {
        ensureColumns();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Insert rows must not be empty");
        }
        return new InsertStmt(table, columns, rows);
    }

    private void ensureColumns() {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Insert columns must not be empty");
        }
    }

    private Expr toExpr(Object value) {
        if (value instanceof Expr expr) {
            return expr;
        }
        return new Expr.Literal(value);
    }
}
