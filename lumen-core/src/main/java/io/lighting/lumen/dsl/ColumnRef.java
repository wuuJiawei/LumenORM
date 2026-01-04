package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.SelectItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ColumnRef {
    private final String tableAlias;
    private final String columnName;

    ColumnRef(String tableAlias, String columnName) {
        this.tableAlias = tableAlias;
        this.columnName = Objects.requireNonNull(columnName, "columnName");
    }

    public static ColumnRef of(String tableAlias, String columnName) {
        return new ColumnRef(tableAlias, columnName);
    }

    public Expr.Column expr() {
        return new Expr.Column(tableAlias, columnName);
    }

    public SelectItem select() {
        return new SelectItem(expr(), null);
    }

    public SelectItem as(String alias) {
        return new SelectItem(expr(), alias);
    }

    public OrderItem asc() {
        return new OrderItem(expr(), true);
    }

    public OrderItem desc() {
        return new OrderItem(expr(), false);
    }

    public Expr.Compare eq(Object value) {
        return compare(Expr.Op.EQ, value);
    }

    public Expr.Compare eq(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.EQ, other.expr());
    }

    public Expr.Compare ne(Object value) {
        return compare(Expr.Op.NE, value);
    }

    public Expr.Compare ne(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.NE, other.expr());
    }

    public Expr.Compare gt(Object value) {
        return compare(Expr.Op.GT, value);
    }

    public Expr.Compare gt(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.GT, other.expr());
    }

    public Expr.Compare ge(Object value) {
        return compare(Expr.Op.GE, value);
    }

    public Expr.Compare ge(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.GE, other.expr());
    }

    public Expr.Compare lt(Object value) {
        return compare(Expr.Op.LT, value);
    }

    public Expr.Compare lt(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.LT, other.expr());
    }

    public Expr.Compare le(Object value) {
        return compare(Expr.Op.LE, value);
    }

    public Expr.Compare le(ColumnRef other) {
        Objects.requireNonNull(other, "other");
        return compare(Expr.Op.LE, other.expr());
    }

    public Expr.Compare isNull() {
        return compare(Expr.Op.EQ, new Expr.Literal(null));
    }

    public Expr.Compare isNotNull() {
        return compare(Expr.Op.NE, new Expr.Literal(null));
    }

    public Expr.Like like(Object pattern) {
        return new Expr.Like(expr(), toExpr(pattern));
    }

    public Expr.In in(Iterable<?> values) {
        Objects.requireNonNull(values, "values");
        List<Expr> rights = new ArrayList<>();
        for (Object value : values) {
            rights.add(toExpr(value));
        }
        return new Expr.In(expr(), rights);
    }

    public Expr.In in(Object... values) {
        Objects.requireNonNull(values, "values");
        List<Expr> rights = new ArrayList<>();
        for (Object value : values) {
            rights.add(toExpr(value));
        }
        return new Expr.In(expr(), rights);
    }

    private Expr.Compare compare(Expr.Op op, Object value) {
        return new Expr.Compare(expr(), op, toExpr(value));
    }

    private Expr toExpr(Object value) {
        if (value instanceof Expr expr) {
            return expr;
        }
        return new Expr.Literal(value);
    }
}
