package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class LogicalDeleteBuilder {
    private final Table tableInfo;
    private Expr where;

    LogicalDeleteBuilder(Table table) {
        this.tableInfo = Objects.requireNonNull(table, "table");
    }

    public LogicalDeleteBuilder where(Expr expr) {
        this.where = Objects.requireNonNull(expr, "expr");
        return this;
    }

    public LogicalDeleteBuilder where(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PredicateBuilder builder = PredicateBuilder.and();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public UpdateStmt build() {
        LogicalDelete logicalDelete = tableInfo.logicalDelete();
        UpdateItem item = logicalDelete.updateItem();
        return new UpdateStmt(tableInfo.ref(), List.of(item), where);
    }
}
