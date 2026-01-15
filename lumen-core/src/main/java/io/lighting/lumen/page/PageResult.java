package io.lighting.lumen.page;

import java.util.List;
import java.util.Objects;

/**
 * Simple pagination result container.
 *
 * @param items    current page items
 * @param page     1-based page index
 * @param pageSize page size
 * @param total    total number of rows
 * @param <T>      element type
 */
public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public PageResult {
        Objects.requireNonNull(items, "items");
    }
}
