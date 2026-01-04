package io.lighting.lumen.sql.ast;

import java.util.List;
import java.util.Objects;

public record SelectStmt(
    List<SelectItem> select,
    TableRef from,
    List<Join> joins,
    Expr where,
    List<Expr> groupBy,
    Expr having,
    List<OrderItem> orderBy,
    Paging paging
) implements Stmt {
    public SelectStmt {
        Objects.requireNonNull(select, "select");
        Objects.requireNonNull(from, "from");
        select = List.copyOf(select);
        joins = joins == null ? List.of() : List.copyOf(joins);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
    }
}
