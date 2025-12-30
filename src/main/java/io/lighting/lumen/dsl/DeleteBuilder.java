package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.DeleteStmt;
import io.lighting.lumen.sql.ast.TableRef;
import java.util.Objects;
import java.util.function.Consumer;

public final class DeleteBuilder {
    private final TableRef table;
    private Expr where;

    DeleteBuilder(TableRef table) {
        this.table = Objects.requireNonNull(table, "table");
    }

    public DeleteBuilder where(Expr expr) {
        this.where = Objects.requireNonNull(expr, "expr");
        return this;
    }

    public DeleteBuilder where(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PredicateBuilder builder = PredicateBuilder.and();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public DeleteStmt build() {
        return new DeleteStmt(table, where);
    }
}
