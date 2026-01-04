package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PredicateBuilder {
    private final LogicalOp op;
    private final List<Expr> items;

    private PredicateBuilder(LogicalOp op) {
        this.op = op;
        this.items = new ArrayList<>();
    }

    public static PredicateBuilder and() {
        return new PredicateBuilder(LogicalOp.AND);
    }

    public static PredicateBuilder or() {
        return new PredicateBuilder(LogicalOp.OR);
    }

    public PredicateBuilder and(Expr expr) {
        return add(expr);
    }

    public PredicateBuilder or(Expr expr) {
        return add(expr);
    }

    public PredicateBuilder andIf(boolean condition, Expr expr) {
        if (condition) {
            add(expr);
        }
        return this;
    }

    public PredicateBuilder orGroup(Consumer<PredicateBuilder> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        PredicateBuilder group = PredicateBuilder.or();
        consumer.accept(group);
        Expr built = group.build();
        if (built != null) {
            add(built);
        }
        return this;
    }

    public Expr build() {
        if (items.isEmpty()) {
            return null;
        }
        return op == LogicalOp.AND ? new Expr.And(List.copyOf(items)) : new Expr.Or(List.copyOf(items));
    }

    private PredicateBuilder add(Expr expr) {
        items.add(Objects.requireNonNull(expr, "expr"));
        return this;
    }

    private enum LogicalOp {
        AND,
        OR
    }
}
