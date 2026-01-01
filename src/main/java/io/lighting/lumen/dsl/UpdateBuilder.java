package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.TableRef;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class UpdateBuilder {
    private final Table tableInfo;
    private final TableRef table;
    private final List<UpdateItem> assignments = new ArrayList<>();
    private Expr where;

    UpdateBuilder(Table table) {
        this.tableInfo = Objects.requireNonNull(table, "table");
        this.table = table.ref();
    }

    public UpdateBuilder set(ColumnRef column, Object value) {
        Objects.requireNonNull(column, "column");
        assignments.add(new UpdateItem(column.expr(), toExpr(value)));
        return this;
    }

    public <T> UpdateBuilder set(PropertyRef<T, ?> column, Object value) {
        Objects.requireNonNull(column, "column");
        return set(tableInfo.col(column), value);
    }

    public UpdateBuilder where(Expr expr) {
        this.where = Objects.requireNonNull(expr, "expr");
        return this;
    }

    public UpdateBuilder where(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PredicateBuilder builder = PredicateBuilder.and();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public UpdateStmt build() {
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("Update assignments must not be empty");
        }
        return new UpdateStmt(table, assignments, where);
    }

    private Expr toExpr(Object value) {
        if (value instanceof Expr expr) {
            return expr;
        }
        return new Expr.Literal(value);
    }
}
