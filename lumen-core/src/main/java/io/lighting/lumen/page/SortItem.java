package io.lighting.lumen.page;

import java.util.Objects;

public record SortItem(String key, SortDirection direction) {
    public SortItem {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Sort key must not be blank");
        }
        Objects.requireNonNull(direction, "direction");
    }

    public static SortItem asc(String key) {
        return new SortItem(key, SortDirection.ASC);
    }

    public static SortItem desc(String key) {
        return new SortItem(key, SortDirection.DESC);
    }
}
