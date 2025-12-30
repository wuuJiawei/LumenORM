package io.lighting.lumen.dsl;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OrderBuilder {
    private final List<OrderItem> items = new ArrayList<>();
    private final Map<Object, List<OrderItem>> allowed = new LinkedHashMap<>();
    private List<OrderItem> selected;

    public OrderBuilder asc(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        ensureDirectOrdering();
        items.add(new OrderItem(expr, true));
        return this;
    }

    public OrderBuilder desc(Expr expr) {
        Objects.requireNonNull(expr, "expr");
        ensureDirectOrdering();
        items.add(new OrderItem(expr, false));
        return this;
    }

    public OrderBuilder add(OrderItem item) {
        ensureDirectOrdering();
        items.add(Objects.requireNonNull(item, "item"));
        return this;
    }

    public <T> OrderBuilder allow(T key, OrderItem... items) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(items, "items");
        ensureWhitelistOrdering();
        if (items.length == 0) {
            throw new IllegalArgumentException("OrderBy allow requires at least one item");
        }
        List<OrderItem> list = new ArrayList<>(items.length);
        for (OrderItem item : items) {
            list.add(Objects.requireNonNull(item, "item"));
        }
        if (allowed.putIfAbsent(key, List.copyOf(list)) != null) {
            throw new IllegalArgumentException("Duplicate order key: " + key);
        }
        return this;
    }

    public <T> OrderBuilder use(T selection, T defaultKey) {
        ensureWhitelistOrdering();
        if (allowed.isEmpty()) {
            throw new IllegalStateException("OrderBy whitelist is empty");
        }
        Object key = selection != null ? selection : defaultKey;
        if (key == null) {
            selected = List.of();
            return this;
        }
        List<OrderItem> resolved = allowed.get(key);
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown order key: " + key);
        }
        selected = resolved;
        return this;
    }

    public <T> OrderBuilder use(T selection) {
        return use(selection, null);
    }

    List<OrderItem> build() {
        if (!allowed.isEmpty()) {
            if (selected == null) {
                throw new IllegalStateException("OrderBy whitelist requires use(...) to select an entry");
            }
            return selected;
        }
        return List.copyOf(items);
    }

    boolean isEmpty() {
        return items.isEmpty() && (selected == null || selected.isEmpty());
    }

    private void ensureDirectOrdering() {
        if (!allowed.isEmpty()) {
            throw new IllegalStateException("Cannot add direct order items when using a whitelist");
        }
    }

    private void ensureWhitelistOrdering() {
        if (!items.isEmpty()) {
            throw new IllegalStateException("Cannot use a whitelist when direct order items are present");
        }
    }
}
