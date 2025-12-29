package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OrderBuilder {
    private final List<OrderItem> items = new ArrayList<>();

    public OrderBuilder asc(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        items.add(new OrderItem(expr, true));
        return this;
    }

    public OrderBuilder desc(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        items.add(new OrderItem(expr, false));
        return this;
    }

    public OrderBuilder add(OrderItem item) {
        items.add(Objects.requireNonNull(item, "item"));
        return this;
    }

    List<OrderItem> build() {
        return List.copyOf(items);
    }

    boolean isEmpty() {
        return items.isEmpty();
    }
}
