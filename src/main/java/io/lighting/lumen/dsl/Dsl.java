package io.lighting.lumen.dsl;

import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.IdentifierMacros;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.SelectItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Dsl {
    private final IdentifierMacros macros;

    public Dsl(EntityMetaRegistry metaRegistry) {
        Objects.requireNonNull(metaRegistry, "metaRegistry");
        this.macros = new IdentifierMacros(metaRegistry);
    }

    public Table table(Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return new Table(entityType, macros, null);
    }

    public SelectBuilder select(SelectItem... items) {
        Objects.requireNonNull(items, "items");
        if (items.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        return new SelectBuilder(List.of(items));
    }

    public SelectBuilder select(Expr... expressions) {
        Objects.requireNonNull(expressions, "expressions");
        if (expressions.length == 0) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        List<SelectItem> items = new ArrayList<>(expressions.length);
        for (Expr expr : expressions) {
            items.add(item(expr));
        }
        return new SelectBuilder(items);
    }

    public static SelectItem item(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        return new SelectItem(expr, null);
    }

    public static SelectItem item(Expr expr, String alias) {
        Objects.requireNonNull(expr, "expr");
        return new SelectItem(expr, alias);
    }

    public static Expr literal(Object value) {
        return new Expr.Literal(value);
    }

    public static Expr param(String name) {
        return new Expr.Param(name);
    }

    public static Expr rawSql(String sql) {
        return new Expr.RawSql(sql);
    }

    public static Functions functions() {
        return new Functions();
    }
}
