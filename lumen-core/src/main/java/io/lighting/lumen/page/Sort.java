package io.lighting.lumen.page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Sort {
    private static final Sort UNSORTED = new Sort(List.of());

    private final List<SortItem> items;

    private Sort(List<SortItem> items) {
        this.items = Collections.unmodifiableList(items);
    }

    public static Sort unsorted() {
        return UNSORTED;
    }

    public static Sort of(List<SortItem> items) {
        if (items == null || items.isEmpty()) {
            return UNSORTED;
        }
        return new Sort(List.copyOf(items));
    }

    public static Sort of(SortItem... items) {
        if (items == null || items.length == 0) {
            return UNSORTED;
        }
        List<SortItem> list = new ArrayList<>(items.length);
        Collections.addAll(list, items);
        return new Sort(List.copyOf(list));
    }

    public static Sort asc(String key) {
        return of(SortItem.asc(key));
    }

    public static Sort desc(String key) {
        return of(SortItem.desc(key));
    }

    public Sort and(Sort other) {
        Objects.requireNonNull(other, "other");
        if (this.items.isEmpty()) {
            return other;
        }
        if (other.items.isEmpty()) {
            return this;
        }
        List<SortItem> merged = new ArrayList<>(this.items.size() + other.items.size());
        merged.addAll(this.items);
        merged.addAll(other.items);
        return new Sort(List.copyOf(merged));
    }

    public Sort and(SortItem item) {
        Objects.requireNonNull(item, "item");
        if (this.items.isEmpty()) {
            return new Sort(List.of(item));
        }
        List<SortItem> merged = new ArrayList<>(this.items.size() + 1);
        merged.addAll(this.items);
        merged.add(item);
        return new Sort(List.copyOf(merged));
    }

    public List<SortItem> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
