package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import java.util.List;
import java.util.Objects;

public final class Functions {

    public Expr.Func fn(String name, Expr... args) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(args, "args");
        return new Expr.Func(name, List.of(args));
    }

    public Expr.Func coalesce(Expr... args) {
        return fn("coalesce", args);
    }

    public Expr.Func lower(Expr arg) {
        return fn("lower", arg);
    }

    public Expr.Func sum(Expr arg) {
        return fn("sum", arg);
    }

    public Expr.Func sub(Expr left, Expr right) {
        return fn("sub", left, right);
    }

    public Expr.Func mul(Expr left, Expr right) {
        return fn("mul", left, right);
    }

    public Expr.Func countDistinct(Expr arg) {
        return fn("count_distinct", arg);
    }

    public Expr.Func jsonText(Expr json, Expr path) {
        return fn("json_text", json, path);
    }

    public Expr.Func dateTrunc(Expr unit, Expr value) {
        return fn("date_trunc", unit, value);
    }
}
